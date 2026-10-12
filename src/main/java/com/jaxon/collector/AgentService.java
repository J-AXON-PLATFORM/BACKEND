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

    AgentService(
            JdbcTemplate jdbc,
            Clock clock,
            AssetLookup assetLookup,
            AgentIdentityRegistry identityRegistry) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.assetLookup = assetLookup;
        this.identityRegistry = identityRegistry;
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
        return new AgentResponse(agentId, assetId, "PENDING", false, null, now, now, 0);
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

    private Optional<AgentRow> findAgent(UUID agentId) {
        return queryAgent(
                """
                SELECT id, asset_id, status, created_at, updated_at, version
                FROM collector.agents WHERE id = ?
                """,
                agentId);
    }

    private Optional<AgentRow> findAgentForUpdate(UUID agentId) {
        return queryAgent(
                """
                SELECT id, asset_id, status, created_at, updated_at, version
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

    private static AgentResponse response(AgentRow agent, AgentIdentityRegistry.KeyState key) {
        Instant updatedAt = agent.updatedAt().isAfter(key.updatedAt()) ? agent.updatedAt() : key.updatedAt();
        return new AgentResponse(
                agent.agentId(),
                agent.assetId(),
                agent.status(),
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
            Instant createdAt,
            Instant updatedAt,
            long version) {}
}

final class AssetNotFoundException extends RuntimeException {}

final class AgentNotFoundException extends RuntimeException {}
