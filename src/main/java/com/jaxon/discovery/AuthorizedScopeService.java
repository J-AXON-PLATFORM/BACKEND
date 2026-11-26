package com.jaxon.discovery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthorizedScopeService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    AuthorizedScopeService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    AuthorizedScopeResponse create(
            AuthorizedScopeCreateRequest request, UUID actorId, UUID correlationId) {
        AuthorizedScopeNormalizer.Name name = AuthorizedScopeNormalizer.normalizeName(request.name());
        String cidr = AuthorizedScopeNormalizer.normalizeCidr(request.cidr());
        UUID scopeId = UUID.randomUUID();
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        try {
            jdbc.update(
                    """
                    INSERT INTO discovery.authorized_scopes
                        (id, name, normalized_name, cidr, status, created_by_user_id,
                         created_at, updated_at, version)
                    VALUES (?, ?, ?, ?::inet, 'ACTIVE', ?, ?, ?, 0)
                    """,
                    scopeId,
                    name.display(),
                    name.normalized(),
                    cidr,
                    actorId,
                    Timestamp.from(now),
                    Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw new AuthorizedScopeConflictException();
        }
        insertOutbox(
                "DISCOVERY_SCOPE_CREATED",
                actorId,
                scopeId,
                0,
                correlationId,
                "'{}'::jsonb",
                List.of(),
                now);
        return find(scopeId, true).orElseThrow();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    AuthorizedScopePage findPage(boolean administrator, int page, int size) {
        String predicate = administrator ? "" : " WHERE status = 'ACTIVE'";
        long totalElements = jdbc.queryForObject(
                "SELECT count(*) FROM discovery.authorized_scopes" + predicate, Long.class);
        List<AuthorizedScopeResponse> items = jdbc.query(
                """
                SELECT id, name, cidr::text, status, created_by_user_id, created_at, updated_at, version
                FROM discovery.authorized_scopes
                """ + predicate + " ORDER BY normalized_name ASC, id ASC LIMIT ? OFFSET ?",
                (resultSet, row) -> response(resultSet),
                size,
                (long) page * size);
        return new AuthorizedScopePage(
                items, page, size, totalElements, Math.toIntExact((totalElements + size - 1) / size));
    }

    @Transactional(readOnly = true)
    Optional<AuthorizedScopeResponse> find(UUID scopeId, boolean administrator) {
        String visibility = administrator ? "" : " AND status = 'ACTIVE'";
        return jdbc.query(
                        """
                        SELECT id, name, cidr::text, status, created_by_user_id, created_at, updated_at, version
                        FROM discovery.authorized_scopes WHERE id = ?
                        """ + visibility,
                        (resultSet, row) -> response(resultSet),
                        scopeId)
                .stream()
                .findFirst();
    }

    @Transactional
    AuthorizedScopeResponse updateStatus(
            UUID scopeId,
            String requestedStatus,
            long expectedVersion,
            UUID actorId,
            UUID correlationId) {
        AuthorizedScopeResponse current = jdbc.query(
                        """
                        SELECT id, name, cidr::text, status, created_by_user_id, created_at, updated_at, version
                        FROM discovery.authorized_scopes WHERE id = ? FOR UPDATE
                        """,
                        (resultSet, row) -> response(resultSet),
                        scopeId)
                .stream()
                .findFirst()
                .orElseThrow(AuthorizedScopeNotFoundException::new);
        if (current.version() != expectedVersion) {
            throw new AuthorizedScopeMutationConflictException();
        }
        if (current.status().equals(requestedStatus)) {
            return current;
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        long newVersion = current.version() + 1;
        jdbc.update(
                "UPDATE discovery.authorized_scopes SET status = ?, updated_at = ?, version = ? WHERE id = ?",
                requestedStatus,
                Timestamp.from(now),
                newVersion,
                scopeId);
        insertOutbox(
                "DISCOVERY_SCOPE_STATUS_CHANGED",
                actorId,
                scopeId,
                newVersion,
                correlationId,
                "jsonb_build_object('previousStatus', ?::text, 'newStatus', ?::text)",
                List.of(current.status(), requestedStatus),
                now);
        return find(scopeId, true).orElseThrow();
    }

    private void insertOutbox(
            String eventType,
            UUID actorId,
            UUID scopeId,
            long resourceVersion,
            UUID correlationId,
            String payloadExpression,
            List<String> payloadValues,
            Instant now) {
        String sql = """
                INSERT INTO discovery.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, '2.0', ?, 'DISCOVERY', 'HUMAN_USER', ?, 'AUTHORIZED_SCOPE', ?, ?,
                        'SUCCESS', ?, %s, ?)
                """.formatted(payloadExpression);
        java.util.ArrayList<Object> parameters = new java.util.ArrayList<>();
        parameters.add(UUID.randomUUID());
        parameters.add(eventType);
        parameters.add(Timestamp.from(now));
        parameters.add(actorId);
        parameters.add(scopeId);
        parameters.add(resourceVersion);
        parameters.add(correlationId);
        parameters.addAll(payloadValues);
        parameters.add(Timestamp.from(now));
        jdbc.update(sql, parameters.toArray());
    }

    private static AuthorizedScopeResponse response(ResultSet resultSet) throws SQLException {
        return new AuthorizedScopeResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("cidr"),
                resultSet.getString("status"),
                resultSet.getObject("created_by_user_id", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"));
    }
}

final class AuthorizedScopeNotFoundException extends RuntimeException {}

final class AuthorizedScopeConflictException extends RuntimeException {}

final class AuthorizedScopeMutationConflictException extends RuntimeException {}
