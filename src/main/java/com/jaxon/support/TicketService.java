package com.jaxon.support;

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
class TicketService {

    private final JdbcTemplate jdbc;
    private final AssetLookup assetLookup;
    private final Clock clock;

    TicketService(JdbcTemplate jdbc, AssetLookup assetLookup, Clock clock) {
        this.jdbc = jdbc;
        this.assetLookup = assetLookup;
        this.clock = clock;
    }

    @Transactional
    TicketResponse create(TicketCreateRequest request, UUID actorId, UUID correlationId) {
        UUID assetId = request.assetId();
        if (assetId != null && (assetId.version() != 4 || !assetLookup.exists(assetId))) {
            throw new AssociatedAssetNotFoundException();
        }

        UUID ticketId = UUID.randomUUID();
        String summary = request.summary().trim();
        String description = request.description().trim();
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        jdbc.update(
                """
                INSERT INTO support.tickets
                    (id, type, summary, description, status, priority, source,
                     requester_user_id, created_by_user_id, assignee_user_id, asset_id,
                     created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'OPEN', 'NORMAL', 'WEB', ?, ?, NULL, ?, ?, ?, 0)
                """,
                ticketId,
                request.type(),
                summary,
                description,
                actorId,
                actorId,
                assetId,
                Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO support.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'TICKET_CREATED', '1.0', ?, 'SUPPORT',
                        'HUMAN_USER', ?, 'TICKET', ?, 0,
                        'SUCCESS', ?, jsonb_build_object(
                            'type', ?::text,
                            'status', 'OPEN',
                            'priority', 'NORMAL',
                            'source', 'WEB'), ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                actorId,
                ticketId,
                correlationId,
                request.type(),
                Timestamp.from(now));
        return new TicketResponse(
                ticketId,
                request.type(),
                summary,
                description,
                "OPEN",
                "NORMAL",
                "WEB",
                actorId,
                actorId,
                null,
                assetId,
                now,
                now,
                0);
    }

    @Transactional(readOnly = true)
    Optional<TicketResponse> findOwn(UUID ticketId, UUID requesterUserId) {
        return jdbc.query(
                        """
                        SELECT id, type, summary, description, status, priority, source,
                               requester_user_id, created_by_user_id, assignee_user_id, asset_id,
                               created_at, updated_at, version
                        FROM support.tickets
                        WHERE id = ? AND requester_user_id = ?
                        """,
                        (resultSet, row) -> new TicketResponse(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("type"),
                                resultSet.getString("summary"),
                                resultSet.getString("description"),
                                resultSet.getString("status"),
                                resultSet.getString("priority"),
                                resultSet.getString("source"),
                                resultSet.getObject("requester_user_id", UUID.class),
                                resultSet.getObject("created_by_user_id", UUID.class),
                                resultSet.getObject("assignee_user_id", UUID.class),
                                resultSet.getObject("asset_id", UUID.class),
                                resultSet.getTimestamp("created_at").toInstant(),
                                resultSet.getTimestamp("updated_at").toInstant(),
                                resultSet.getLong("version")),
                        ticketId,
                        requesterUserId)
                .stream()
                .findFirst();
    }
}

final class AssociatedAssetNotFoundException extends RuntimeException {}
