package com.jaxon.collector;

import com.jaxon.identity.AgentIdentityRegistry;
import com.jaxon.inventory.AssetLookup;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AgentService {

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final AssetLookup assetLookup;
    private final AgentIdentityRegistry identityRegistry;
    private final SftpGoProvisioner sftpGoProvisioner;
    private final AgentOperationalStateService operationalStates;

    AgentService(
            JdbcTemplate jdbc,
            Clock clock,
            AssetLookup assetLookup,
            AgentIdentityRegistry identityRegistry,
            SftpGoProvisioner sftpGoProvisioner,
            AgentOperationalStateService operationalStates) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.assetLookup = assetLookup;
        this.identityRegistry = identityRegistry;
        this.sftpGoProvisioner = sftpGoProvisioner;
        this.operationalStates = operationalStates;
    }

    @Transactional
    AgentResponse create(AgentCreateRequest request, UUID actorId, UUID correlationId) {
        UUID assetId = UUID.fromString(request.assetId());
        if (!assetLookup.exists(assetId)) {
            throw new AssetNotFoundException();
        }
        UUID agentId = UUID.randomUUID();
        Instant now = now();
        identityRegistry.create(agentId, now);
        jdbc.update(
                """
                INSERT INTO collector.agents
                    (id, asset_id, status, created_at, updated_at, version)
                VALUES (?, ?, 'PENDING', ?, ?, 0)
                """,
                agentId,
                assetId,
                Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO collector.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'AGENT_PRE_REGISTERED', '1.0', ?, 'COLLECTOR',
                        'HUMAN_USER', ?, 'AGENT', ?, 0,
                        'SUCCESS', ?, '{}'::jsonb, ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                actorId,
                agentId,
                correlationId,
                Timestamp.from(now));
        return response(
                new AgentRow(agentId, assetId, "PENDING", null, null, now, now, 0),
                new AgentIdentityRegistry.KeyState(false, null, now, 0),
                AgentOperationalStateService.AgentOperationalState.unknown());
    }

    @Transactional(readOnly = true)
    Optional<AgentResponse> find(UUID agentId) {
        return findAgent(agentId).map(this::response);
    }

    @Transactional
    AgentResponse registerPublicKey(
            UUID agentId, AgentSshKeyRequest request, UUID actorId, UUID correlationId) {
        AgentRow agent = findAgentForUpdate(agentId).orElseThrow(AgentNotFoundException::new);
        AgentIdentityRegistry.KeyState key = identityRegistry.registerPublicKey(
                agentId, request.publicKey(), actorId, correlationId, now());
        return response(agent, key);
    }

    @Transactional
    AgentActivationResponse activate(UUID agentId, UUID actorId, UUID correlationId) {
        AgentRow agent = findAgentForUpdate(agentId).orElseThrow(AgentNotFoundException::new);
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                resultSet -> null,
                agent.assetId().toString());
        if (!assetLookup.isActive(agent.assetId())) {
            throw new AssetNotActiveException();
        }
        Integer activeAgents = jdbc.queryForObject(
                """
                SELECT count(*) FROM collector.agents
                WHERE asset_id = ? AND status = 'ACTIVE' AND id <> ?
                """,
                Integer.class,
                agent.assetId(),
                agentId);
        if (activeAgents != null && activeAgents > 0) {
            throw new AssetActiveAgentConflictException();
        }
        if (!"PENDING".equals(agent.status()) && !"ACTIVE".equals(agent.status())) {
            throw new AgentStateConflictException();
        }
        AgentIdentityRegistry.RegisteredPublicKey key;
        try {
            key = identityRegistry.requireRegisteredPublicKey(agentId);
        } catch (IllegalStateException exception) {
            throw new AgentPublicKeyRequiredException();
        }
        AgentActivationResponse connection = sftpGoProvisioner.ensureProvisioned(agentId, key.publicKey());
        if ("ACTIVE".equals(agent.status())) {
            if (!connection.username().equals(agent.sftpUsername())) {
                throw new SftpProvisioningConflictException();
            }
            return connection;
        }

        Instant occurredAt = now();
        long version = agent.version() + 1;
        jdbc.update(
                """
                UPDATE collector.agents
                SET status = 'ACTIVE', sftp_username = ?, activated_at = ?, updated_at = ?, version = ?
                WHERE id = ? AND version = ?
                """,
                connection.username(),
                Timestamp.from(occurredAt),
                Timestamp.from(occurredAt),
                version,
                agentId,
                agent.version());
        jdbc.update(
                """
                INSERT INTO collector.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'AGENT_ACTIVATED', '1.0', ?, 'COLLECTOR',
                        'HUMAN_USER', ?, 'AGENT', ?, ?,
                        'SUCCESS', ?, '{}'::jsonb, ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(occurredAt),
                actorId,
                agentId,
                version,
                correlationId,
                Timestamp.from(occurredAt));
        return connection;
    }

    private Optional<AgentRow> findAgent(UUID agentId) {
        return queryAgent(
                """
                SELECT id, asset_id, status, sftp_username, activated_at, created_at, updated_at, version
                FROM collector.agents WHERE id = ?
                """,
                agentId);
    }

    private Optional<AgentRow> findAgentForUpdate(UUID agentId) {
        return queryAgent(
                """
                SELECT id, asset_id, status, sftp_username, activated_at, created_at, updated_at, version
                FROM collector.agents WHERE id = ? FOR UPDATE
                """,
                agentId);
    }

    private Optional<AgentRow> queryAgent(String sql, UUID agentId) {
        return jdbc.query(
                        sql,
                        (resultSet, row) -> new AgentRow(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getObject("asset_id", UUID.class),
                                resultSet.getString("status"),
                                resultSet.getString("sftp_username"),
                                resultSet.getTimestamp("activated_at") == null
                                        ? null
                                        : resultSet.getTimestamp("activated_at").toInstant(),
                                resultSet.getTimestamp("created_at").toInstant(),
                                resultSet.getTimestamp("updated_at").toInstant(),
                                resultSet.getLong("version")),
                        agentId)
                .stream()
                .findFirst();
    }

    private AgentResponse response(AgentRow agent) {
        AgentIdentityRegistry.KeyState key = identityRegistry.find(agent.agentId())
                .orElseThrow(IllegalStateException::new);
        return response(agent, key);
    }

    private AgentResponse response(AgentRow agent, AgentIdentityRegistry.KeyState key) {
        return response(agent, key, operationalStates.find(agent.agentId()));
    }

    private static AgentResponse response(
            AgentRow agent,
            AgentIdentityRegistry.KeyState key,
            AgentOperationalStateService.AgentOperationalState operationalState) {
        Instant updatedAt = agent.updatedAt().isAfter(key.updatedAt()) ? agent.updatedAt() : key.updatedAt();
        return new AgentResponse(
                agent.agentId(),
                agent.assetId(),
                agent.status(),
                operationalState.operationalStatus(),
                operationalState.lastSeenAt(),
                operationalState.lastSnapshotAt(),
                key.publicKeyRegistered(),
                key.publicKeyFingerprint(),
                agent.createdAt(),
                updatedAt,
                agent.version());
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private record AgentRow(
            UUID agentId,
            UUID assetId,
            String status,
            String sftpUsername,
            Instant activatedAt,
            Instant createdAt,
            Instant updatedAt,
            long version) {}
}

final class AssetNotFoundException extends RuntimeException {}

final class AgentNotFoundException extends RuntimeException {}
