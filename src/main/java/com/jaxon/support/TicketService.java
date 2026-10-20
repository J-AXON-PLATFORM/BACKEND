package com.jaxon.support;

import com.jaxon.inventory.AssetLookup;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    TicketPage findOwnPage(UUID requesterUserId, int page, int size) {
        long totalElements = jdbc.queryForObject(
                "SELECT count(*) FROM support.tickets WHERE requester_user_id = ?",
                Long.class,
                requesterUserId);
        List<TicketResponse> items = jdbc.query(
                """
                SELECT id, type, summary, description, status, priority, source,
                       requester_user_id, created_by_user_id, assignee_user_id, asset_id,
                       created_at, updated_at, version
                FROM support.tickets
                WHERE requester_user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                (resultSet, row) -> ticket(resultSet),
                requesterUserId,
                size,
                (long) page * size);
        return new TicketPage(items, page, size, totalElements, totalPages(totalElements, size));
    }

    @Transactional
    Optional<TicketCommentResponse> addOwnComment(
            UUID ticketId,
            UUID requesterUserId,
            String requestedBody,
            UUID correlationId) {
        if (!owns(ticketId, requesterUserId)) {
            return Optional.empty();
        }
        String body = requestedBody.trim();
        if (body.isEmpty() || body.length() > 10_000) {
            throw new InvalidCommentException();
        }
        UUID commentId = UUID.randomUUID();
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        jdbc.update(
                """
                INSERT INTO support.ticket_comments
                    (id, ticket_id, visibility, author_user_id, body, created_at)
                VALUES (?, ?, 'PUBLIC', ?, ?, ?)
                """,
                commentId,
                ticketId,
                requesterUserId,
                body,
                Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO support.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'TICKET_COMMENT_ADDED', '1.0', ?, 'SUPPORT',
                        'HUMAN_USER', ?, 'TICKET', ?, 0,
                        'SUCCESS', ?, jsonb_build_object(
                            'commentId', ?::text,
                            'visibility', 'PUBLIC'), ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                requesterUserId,
                ticketId,
                correlationId,
                commentId,
                Timestamp.from(now));
        return Optional.of(new TicketCommentResponse(
                commentId, ticketId, "PUBLIC", requesterUserId, body, now));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Optional<TicketCommentPage> findOwnComments(
            UUID ticketId, UUID requesterUserId, int page, int size) {
        if (!owns(ticketId, requesterUserId)) {
            return Optional.empty();
        }
        long totalElements = jdbc.queryForObject(
                "SELECT count(*) FROM support.ticket_comments WHERE ticket_id = ? AND visibility = 'PUBLIC'",
                Long.class,
                ticketId);
        List<TicketCommentResponse> items = jdbc.query(
                """
                SELECT id, ticket_id, visibility, author_user_id, body, created_at
                FROM support.ticket_comments
                WHERE ticket_id = ? AND visibility = 'PUBLIC'
                ORDER BY created_at ASC, id ASC
                LIMIT ? OFFSET ?
                """,
                (resultSet, row) -> new TicketCommentResponse(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("ticket_id", UUID.class),
                        resultSet.getString("visibility"),
                        resultSet.getObject("author_user_id", UUID.class),
                        resultSet.getString("body"),
                        resultSet.getTimestamp("created_at").toInstant()),
                ticketId,
                size,
                (long) page * size);
        return Optional.of(new TicketCommentPage(
                items, page, size, totalElements, totalPages(totalElements, size)));
    }

    private boolean owns(UUID ticketId, UUID requesterUserId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM support.tickets WHERE id = ? AND requester_user_id = ?)",
                Boolean.class,
                ticketId,
                requesterUserId));
    }

    private static TicketResponse ticket(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new TicketResponse(
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
                resultSet.getLong("version"));
    }

    private static int totalPages(long totalElements, int size) {
        return Math.toIntExact((totalElements + size - 1) / size);
    }
}

final class AssociatedAssetNotFoundException extends RuntimeException {}

final class InvalidCommentException extends RuntimeException {}
