package com.jaxon.discovery;

import com.jaxon.identity.DiscoveryExecutorIdentityRegistry;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DiscoveryExecutorService {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final Clock clock;
    private final DiscoveryExecutorIdentityRegistry identities;

    DiscoveryExecutorService(
            JdbcTemplate jdbc,
            NamedParameterJdbcTemplate namedJdbc,
            Clock clock,
            DiscoveryExecutorIdentityRegistry identities) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
        this.clock = clock;
        this.identities = identities;
    }

    @Transactional
    DiscoveryExecutorResponse create(
            DiscoveryExecutorCreateRequest request, UUID actorId, UUID correlationId) {
        AuthorizedScopeNormalizer.Name name = AuthorizedScopeNormalizer.normalizeName(request.name());
        List<UUID> scopeIds = scopeIds(request.scopeIds());
        ensureActiveScopes(scopeIds);
        UUID executorId = UUID.randomUUID();
        Instant now = now();
        identities.create(executorId, now);
        try {
            jdbc.update(
                    """
                    INSERT INTO discovery.discovery_executors
                        (id, name, normalized_name, status, created_by_user_id,
                         created_at, updated_at, version)
                    VALUES (?, ?, ?, 'PENDING', ?, ?, ?, 0)
                    """,
                    executorId,
                    name.display(),
                    name.normalized(),
                    actorId,
                    Timestamp.from(now),
                    Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw new DiscoveryExecutorConflictException();
        }
        for (UUID scopeId : scopeIds) {
            jdbc.update(
                    """
                    INSERT INTO discovery.discovery_executor_scopes
                        (executor_id, scope_id, associated_at)
                    VALUES (?, ?, ?)
                    """,
                    executorId,
                    scopeId,
                    Timestamp.from(now));
        }
        jdbc.update(
                """
                INSERT INTO discovery.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'DISCOVERY_EXECUTOR_PRE_REGISTERED', '1.0', ?, 'DISCOVERY',
                        'HUMAN_USER', ?, 'DISCOVERY_EXECUTOR', ?, 0,
                        'SUCCESS', ?, '{"status":"PENDING"}'::jsonb, ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                actorId,
                executorId,
                correlationId,
                Timestamp.from(now));
        return response(row(executorId).orElseThrow());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    DiscoveryExecutorPage findPage(int page, int size) {
        long total = jdbc.queryForObject(
                "SELECT count(*) FROM discovery.discovery_executors", Long.class);
        List<DiscoveryExecutorResponse> items = jdbc.query(
                        """
                        SELECT id, name, status, created_by_user_id, created_at, updated_at, version
                        FROM discovery.discovery_executors
                        ORDER BY normalized_name ASC, id ASC LIMIT ? OFFSET ?
                        """,
                        (resultSet, index) -> executorRow(resultSet),
                        size,
                        (long) page * size)
                .stream()
                .map(this::response)
                .toList();
        return new DiscoveryExecutorPage(
                items, page, size, total, Math.toIntExact((total + size - 1) / size));
    }

    @Transactional(readOnly = true)
    Optional<DiscoveryExecutorResponse> find(UUID executorId) {
        return row(executorId).map(this::response);
    }

    @Transactional
    DiscoveryExecutorResponse registerPublicKey(
            UUID executorId,
            DiscoveryExecutorSshKeyRequest request,
            UUID actorId,
            UUID correlationId) {
        ExecutorRow executor = rowForUpdate(executorId).orElseThrow(DiscoveryExecutorNotFoundException::new);
        DiscoveryExecutorIdentityRegistry.KeyState key;
        try {
            key = identities.registerPublicKey(
                    executorId, request.publicKey(), actorId, correlationId, now());
        } catch (DiscoveryExecutorIdentityRegistry.InvalidPublicKeyException exception) {
            throw new InvalidDiscoveryExecutorPublicKeyException();
        } catch (DiscoveryExecutorIdentityRegistry.PublicKeyConflictException exception) {
            throw new DiscoveryExecutorPublicKeyConflictException();
        }
        return response(executor, key);
    }

    private void ensureActiveScopes(List<UUID> scopeIds) {
        List<ScopeState> found = namedJdbc.query(
                """
                SELECT id, status FROM discovery.authorized_scopes
                WHERE id IN (:ids)
                ORDER BY id
                FOR SHARE
                """,
                new MapSqlParameterSource("ids", scopeIds),
                (resultSet, index) -> new ScopeState(
                        resultSet.getObject("id", UUID.class), resultSet.getString("status")));
        if (found.size() != scopeIds.size()) {
            throw new DiscoveryExecutorScopeNotFoundException();
        }
        if (found.stream().anyMatch(scope -> !"ACTIVE".equals(scope.status()))) {
            throw new DiscoveryExecutorScopeNotActiveException();
        }
    }

    private static List<UUID> scopeIds(List<String> values) {
        try {
            List<UUID> parsed = values.stream().map(UUID::fromString).toList();
            if (parsed.stream().anyMatch(id -> id.version() != 4)
                    || new HashSet<>(parsed).size() != parsed.size()) {
                throw new IllegalArgumentException();
            }
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new InvalidDiscoveryExecutorScopeIdsException();
        }
    }

    private Optional<ExecutorRow> row(UUID executorId) {
        return queryRow(
                """
                SELECT id, name, status, created_by_user_id, created_at, updated_at, version
                FROM discovery.discovery_executors WHERE id = ?
                """,
                executorId);
    }

    private Optional<ExecutorRow> rowForUpdate(UUID executorId) {
        return queryRow(
                """
                SELECT id, name, status, created_by_user_id, created_at, updated_at, version
                FROM discovery.discovery_executors WHERE id = ? FOR UPDATE
                """,
                executorId);
    }

    private Optional<ExecutorRow> queryRow(String sql, UUID executorId) {
        return jdbc.query(sql, (resultSet, index) -> executorRow(resultSet), executorId).stream()
                .findFirst();
    }

    private DiscoveryExecutorResponse response(ExecutorRow executor) {
        DiscoveryExecutorIdentityRegistry.KeyState key = identities.find(executor.executorId())
                .orElseThrow(IllegalStateException::new);
        return response(executor, key);
    }

    private DiscoveryExecutorResponse response(
            ExecutorRow executor, DiscoveryExecutorIdentityRegistry.KeyState key) {
        List<UUID> scopes = jdbc.queryForList(
                """
                SELECT association.scope_id
                FROM discovery.discovery_executor_scopes association
                JOIN discovery.authorized_scopes scope ON scope.id = association.scope_id
                WHERE association.executor_id = ?
                ORDER BY scope.normalized_name ASC, scope.id ASC
                """,
                UUID.class,
                executor.executorId());
        Instant updatedAt = executor.updatedAt().isAfter(key.updatedAt())
                ? executor.updatedAt()
                : key.updatedAt();
        return new DiscoveryExecutorResponse(
                executor.executorId(),
                executor.name(),
                scopes,
                executor.status(),
                key.publicKeyRegistered(),
                key.publicKeyFingerprint(),
                executor.createdByUserId(),
                executor.createdAt(),
                updatedAt,
                executor.version());
    }

    private static ExecutorRow executorRow(ResultSet resultSet) throws SQLException {
        return new ExecutorRow(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("status"),
                resultSet.getObject("created_by_user_id", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"));
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private record ExecutorRow(
            UUID executorId,
            String name,
            String status,
            UUID createdByUserId,
            Instant createdAt,
            Instant updatedAt,
            long version) {}

    private record ScopeState(UUID scopeId, String status) {}
}

final class DiscoveryExecutorNotFoundException extends RuntimeException {}

final class DiscoveryExecutorConflictException extends RuntimeException {}

final class DiscoveryExecutorScopeNotFoundException extends RuntimeException {}

final class DiscoveryExecutorScopeNotActiveException extends RuntimeException {}

final class InvalidDiscoveryExecutorScopeIdsException extends RuntimeException {}

final class InvalidDiscoveryExecutorPublicKeyException extends RuntimeException {}

final class DiscoveryExecutorPublicKeyConflictException extends RuntimeException {}
