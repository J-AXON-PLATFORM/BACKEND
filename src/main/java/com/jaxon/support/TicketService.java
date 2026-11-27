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
                VALUES (?, 'TICKET_CREATED', '2.0', ?, 'SUPPORT',
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
        List<TicketListItemResponse> items = jdbc.query(
                """
                SELECT id, type, summary, status, priority, source,
                       requester_user_id, created_by_user_id, assignee_user_id, asset_id,
                       created_at, updated_at, version
                FROM support.tickets
                WHERE requester_user_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                (resultSet, row) -> new TicketListItemResponse(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("type"),
                        resultSet.getString("summary"),
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
                requesterUserId,
                size,
                (long) page * size);
        return new TicketPage(items, page, size, totalElements, totalPages(totalElements, size));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    TicketPage findTechnicianQueue(UUID technicianUserId, int page, int size) {
        long totalElements = jdbc.queryForObject(
                """
                SELECT count(*)
                FROM support.tickets
                WHERE status <> 'CLOSED'
                  AND (assignee_user_id IS NULL OR assignee_user_id = ?)
                """,
                Long.class,
                technicianUserId);
        List<TicketListItemResponse> items = jdbc.query(
                """
                SELECT id, type, summary, status, priority, source,
                       requester_user_id, created_by_user_id, assignee_user_id, asset_id,
                       created_at, updated_at, version
                FROM support.tickets
                WHERE status <> 'CLOSED'
                  AND (assignee_user_id IS NULL OR assignee_user_id = ?)
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """,
                (resultSet, row) -> ticketListItem(resultSet),
                technicianUserId,
                size,
                (long) page * size);
        return new TicketPage(items, page, size, totalElements, totalPages(totalElements, size));
    }

    @Transactional(readOnly = true)
    Optional<TicketResponse> findForTechnician(UUID ticketId, UUID technicianUserId) {
        return jdbc.query(
                        """
                        SELECT id, type, summary, description, status, priority, source,
                               requester_user_id, created_by_user_id, assignee_user_id, asset_id,
                               created_at, updated_at, version
                        FROM support.tickets
                        WHERE id = ?
                          AND (
                              (status <> 'CLOSED' AND assignee_user_id IS NULL)
                              OR assignee_user_id = ?
                          )
                        """,
                        (resultSet, row) -> ticketResponse(resultSet),
                        ticketId,
                        technicianUserId)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Optional<TicketCommentPage> findCommentsForTechnician(
            UUID ticketId, UUID technicianUserId, int page, int size) {
        if (!isAvailableToTechnician(ticketId, technicianUserId)) {
            return Optional.empty();
        }
        return Optional.of(findPublicComments(ticketId, page, size));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Optional<TicketCommentPage> findConversationForTechnician(
            UUID ticketId, UUID technicianUserId, int page, int size) {
        if (!isAssignedToTechnician(ticketId, technicianUserId)) {
            return Optional.empty();
        }
        long totalElements = jdbc.queryForObject(
                "SELECT count(*) FROM support.ticket_comments WHERE ticket_id = ?",
                Long.class,
                ticketId);
        List<TicketCommentResponse> items = jdbc.query(
                """
                SELECT id, ticket_id, visibility, author_user_id, body, created_at
                FROM support.ticket_comments
                WHERE ticket_id = ?
                ORDER BY created_at ASC, id ASC
                LIMIT ? OFFSET ?
                """,
                (resultSet, row) -> ticketCommentResponse(resultSet),
                ticketId,
                size,
                (long) page * size);
        return Optional.of(new TicketCommentPage(items, page, size, totalElements, totalPages(totalElements, size)));
    }

    @Transactional
    TicketResponse claim(
            UUID ticketId,
            UUID technicianUserId,
            long expectedVersion,
            UUID correlationId) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        Optional<ClaimResult> result = jdbc.query(
                """
                WITH current_ticket AS (
                    SELECT id, type, summary, description, status, priority, source,
                           requester_user_id, created_by_user_id, assignee_user_id, asset_id,
                           created_at, updated_at, version
                    FROM support.tickets
                    WHERE id = ?
                    FOR UPDATE
                ), updated_ticket AS (
                    UPDATE support.tickets ticket
                    SET assignee_user_id = ?, updated_at = ?, version = ticket.version + 1
                    FROM current_ticket current
                    WHERE ticket.id = current.id
                      AND current.status <> 'CLOSED'
                      AND current.version = ?
                      AND current.assignee_user_id IS NULL
                    RETURNING ticket.id, ticket.type, ticket.summary, ticket.description,
                              ticket.status, ticket.priority, ticket.source,
                              ticket.requester_user_id, ticket.created_by_user_id,
                              ticket.assignee_user_id, ticket.asset_id,
                              ticket.created_at, ticket.updated_at, ticket.version
                )
                SELECT CASE
                           WHEN updated.id IS NOT NULL THEN 'CLAIMED'
                           WHEN current.version <> ? THEN 'VERSION_CONFLICT'
                           WHEN current.status = 'CLOSED' THEN 'ASSIGNMENT_CONFLICT'
                           WHEN current.assignee_user_id = ? THEN 'UNCHANGED'
                           ELSE 'ASSIGNMENT_CONFLICT'
                       END AS outcome,
                       COALESCE(updated.id, current.id) AS id,
                       COALESCE(updated.type, current.type) AS type,
                       COALESCE(updated.summary, current.summary) AS summary,
                       COALESCE(updated.description, current.description) AS description,
                       COALESCE(updated.status, current.status) AS status,
                       COALESCE(updated.priority, current.priority) AS priority,
                       COALESCE(updated.source, current.source) AS source,
                       COALESCE(updated.requester_user_id, current.requester_user_id) AS requester_user_id,
                       COALESCE(updated.created_by_user_id, current.created_by_user_id) AS created_by_user_id,
                       COALESCE(updated.assignee_user_id, current.assignee_user_id) AS assignee_user_id,
                       COALESCE(updated.asset_id, current.asset_id) AS asset_id,
                       COALESCE(updated.created_at, current.created_at) AS created_at,
                       COALESCE(updated.updated_at, current.updated_at) AS updated_at,
                       COALESCE(updated.version, current.version) AS version
                FROM current_ticket current
                LEFT JOIN updated_ticket updated ON updated.id = current.id
                """,
                resultSet -> {
                    if (!resultSet.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new ClaimResult(
                            resultSet.getString("outcome"), ticketResponse(resultSet)));
                },
                ticketId,
                technicianUserId,
                Timestamp.from(now),
                expectedVersion,
                expectedVersion,
                technicianUserId);
        ClaimResult claim = result.orElseThrow(TicketNotFoundException::new);
        if (claim.outcome().equals("VERSION_CONFLICT")
                || claim.outcome().equals("ASSIGNMENT_CONFLICT")) {
            throw new TicketClaimConflictException();
        }
        if (claim.outcome().equals("CLAIMED")) {
            jdbc.update(
                    """
                    INSERT INTO support.outbox_events
                        (event_id, event_type, event_version, occurred_at, producer_module,
                         actor_type, actor_id, resource_type, resource_id, resource_version,
                         result, correlation_id, payload, next_attempt_at)
                    VALUES (?, 'TICKET_ASSIGNED', '2.0', ?, 'SUPPORT',
                            'HUMAN_USER', ?, 'TICKET', ?, ?,
                            'SUCCESS', ?, jsonb_build_object('assigneeUserId', ?::text), ?)
                    """,
                    UUID.randomUUID(),
                    Timestamp.from(now),
                    technicianUserId,
                    ticketId,
                    claim.ticket().version(),
                    correlationId,
                    technicianUserId,
                    Timestamp.from(now));
        }
        return claim.ticket();
    }

    @Transactional
    TicketResponse updatePriority(
            UUID ticketId,
            UUID technicianUserId,
            String priority,
            long expectedVersion,
            UUID correlationId) {
        TicketResponse ticket = findAssignedForUpdate(ticketId, technicianUserId);
        requireCurrentOperationalTicket(ticket, expectedVersion);
        if (ticket.priority().equals(priority)) {
            return ticket;
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        jdbc.update(
                "UPDATE support.tickets SET priority = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                priority,
                Timestamp.from(now),
                ticketId);
        TicketResponse updated = findAssignedForUpdate(ticketId, technicianUserId);
        insertMutationEvent(
                "TICKET_PRIORITY_CHANGED",
                technicianUserId,
                ticketId,
                updated.version(),
                correlationId,
                "previousPriority",
                ticket.priority(),
                "newPriority",
                priority,
                now);
        return updated;
    }

    @Transactional
    TicketResponse updateStatus(
            UUID ticketId,
            UUID technicianUserId,
            String status,
            long expectedVersion,
            UUID correlationId) {
        TicketResponse ticket = findAssignedForUpdate(ticketId, technicianUserId);
        requireCurrentOperationalTicket(ticket, expectedVersion);
        if (ticket.status().equals(status)) {
            return ticket;
        }
        if (!validStatusTransition(ticket.status(), status)) {
            throw new TicketMutationConflictException();
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        jdbc.update(
                "UPDATE support.tickets SET status = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                status,
                Timestamp.from(now),
                ticketId);
        TicketResponse updated = findAssignedForUpdate(ticketId, technicianUserId);
        insertMutationEvent(
                "TICKET_STATUS_CHANGED",
                technicianUserId,
                ticketId,
                updated.version(),
                correlationId,
                "previousStatus",
                ticket.status(),
                "newStatus",
                status,
                now);
        return updated;
    }

    @Transactional
    TicketResponse resolve(
            UUID ticketId,
            UUID technicianUserId,
            String requestedSummary,
            long expectedVersion,
            UUID correlationId) {
        TicketResponse ticket = findAssignedForUpdate(ticketId, technicianUserId);
        requireVersion(ticket, expectedVersion);
        if (!ticket.status().equals("IN_PROGRESS")
                && !ticket.status().equals("WAITING_FOR_REQUESTER")) {
            throw new TicketMutationConflictException();
        }
        return applyLifecycleTransition(
                ticket,
                technicianUserId,
                "RESOLVED",
                "RESOLVED",
                requestedSummary.trim(),
                null,
                null,
                correlationId);
    }

    @Transactional
    TicketResponse close(
            UUID ticketId,
            UUID technicianUserId,
            String closeReason,
            long expectedVersion,
            UUID correlationId) {
        TicketResponse ticket = findAssignedForUpdate(ticketId, technicianUserId);
        requireVersion(ticket, expectedVersion);
        boolean resolvedClosure = ticket.status().equals("RESOLVED") && closeReason.equals("RESOLVED");
        boolean directClosure = !ticket.status().equals("RESOLVED")
                && !ticket.status().equals("CLOSED")
                && !closeReason.equals("RESOLVED");
        if (!resolvedClosure && !directClosure) {
            throw new TicketMutationConflictException();
        }
        return applyLifecycleTransition(
                ticket,
                technicianUserId,
                "CLOSED",
                "CLOSED",
                null,
                closeReason,
                null,
                correlationId);
    }

    @Transactional
    TicketResponse reopen(
            UUID ticketId,
            UUID actorId,
            String requestedReason,
            long expectedVersion,
            UUID correlationId) {
        TicketResponse ticket = findReopenableForUpdate(ticketId, actorId);
        requireVersion(ticket, expectedVersion);
        if (!ticket.status().equals("RESOLVED") && !ticket.status().equals("CLOSED")) {
            throw new TicketMutationConflictException();
        }
        return applyLifecycleTransition(
                ticket,
                actorId,
                "REOPENED",
                "OPEN",
                null,
                null,
                requestedReason.trim(),
                correlationId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Optional<TicketLifecyclePage> findOwnLifecycle(
            UUID ticketId, UUID requesterUserId, int page, int size) {
        if (!owns(ticketId, requesterUserId)) {
            return Optional.empty();
        }
        return Optional.of(findLifecycle(ticketId, page, size));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Optional<TicketLifecyclePage> findLifecycleForTechnician(
            UUID ticketId, UUID technicianUserId, int page, int size) {
        if (!isAssignedToTechnicianIncludingClosed(ticketId, technicianUserId)) {
            return Optional.empty();
        }
        return Optional.of(findLifecycle(ticketId, page, size));
    }

    @Transactional
    Optional<TicketCommentResponse> addOwnComment(
            UUID ticketId,
            UUID requesterUserId,
            String requestedBody,
            UUID correlationId) {
        Optional<Long> ticketVersion = jdbc.query(
                """
                SELECT version
                FROM support.tickets
                WHERE id = ? AND requester_user_id = ? AND status <> 'CLOSED'
                FOR UPDATE
                """,
                resultSet -> resultSet.next()
                        ? Optional.of(resultSet.getLong("version"))
                        : Optional.empty(),
                ticketId,
                requesterUserId);
        if (ticketVersion.isEmpty()) {
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
                VALUES (?, 'TICKET_COMMENT_ADDED', '2.0', ?, 'SUPPORT',
                        'HUMAN_USER', ?, 'TICKET', ?, ?,
                        'SUCCESS', ?, jsonb_build_object(
                            'commentId', ?::text,
                            'visibility', 'PUBLIC'), ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                requesterUserId,
                ticketId,
                ticketVersion.get(),
                correlationId,
                commentId,
                Timestamp.from(now));
        return Optional.of(new TicketCommentResponse(
                commentId, ticketId, "PUBLIC", requesterUserId, body, now));
    }

    @Transactional
    Optional<TicketCommentResponse> addTechnicianComment(
            UUID ticketId,
            UUID technicianUserId,
            String visibility,
            String requestedBody,
            UUID correlationId) {
        Optional<ConversationTicket> ticket = jdbc.query(
                """
                SELECT status, assignee_user_id, version
                FROM support.tickets
                WHERE id = ?
                FOR UPDATE
                """,
                resultSet -> resultSet.next()
                        ? Optional.of(new ConversationTicket(
                                resultSet.getString("status"),
                                resultSet.getObject("assignee_user_id", UUID.class),
                                resultSet.getLong("version")))
                        : Optional.empty(),
                ticketId);
        if (ticket.isEmpty()
                || ticket.get().status().equals("CLOSED")
                || !technicianUserId.equals(ticket.get().assigneeUserId())) {
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
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                commentId,
                ticketId,
                visibility,
                technicianUserId,
                body,
                Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO support.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, 'TICKET_COMMENT_ADDED', '2.0', ?, 'SUPPORT',
                        'HUMAN_USER', ?, 'TICKET', ?, ?,
                        'SUCCESS', ?, jsonb_build_object(
                            'commentId', ?::text,
                            'visibility', ?::text), ?)
                """,
                UUID.randomUUID(),
                Timestamp.from(now),
                technicianUserId,
                ticketId,
                ticket.get().version(),
                correlationId,
                commentId,
                visibility,
                Timestamp.from(now));
        return Optional.of(new TicketCommentResponse(
                commentId, ticketId, visibility, technicianUserId, body, now));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    Optional<TicketCommentPage> findOwnComments(
            UUID ticketId, UUID requesterUserId, int page, int size) {
        if (!owns(ticketId, requesterUserId)) {
            return Optional.empty();
        }
        return Optional.of(findPublicComments(ticketId, page, size));
    }

    private TicketCommentPage findPublicComments(UUID ticketId, int page, int size) {
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
                (resultSet, row) -> ticketCommentResponse(resultSet),
                ticketId,
                size,
                (long) page * size);
        return new TicketCommentPage(items, page, size, totalElements, totalPages(totalElements, size));
    }

    private TicketLifecyclePage findLifecycle(UUID ticketId, int page, int size) {
        long totalElements = jdbc.queryForObject(
                "SELECT count(*) FROM support.ticket_lifecycle_events WHERE ticket_id = ?",
                Long.class,
                ticketId);
        List<TicketLifecycleEntryResponse> items = jdbc.query(
                """
                SELECT id, event_type, from_status, to_status, resolution_summary,
                       close_reason, reopen_reason, actor_user_id, occurred_at, resource_version
                FROM support.ticket_lifecycle_events
                WHERE ticket_id = ?
                ORDER BY occurred_at ASC, id ASC
                LIMIT ? OFFSET ?
                """,
                (resultSet, row) -> new TicketLifecycleEntryResponse(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("event_type"),
                        resultSet.getString("from_status"),
                        resultSet.getString("to_status"),
                        resultSet.getString("resolution_summary"),
                        resultSet.getString("close_reason"),
                        resultSet.getString("reopen_reason"),
                        resultSet.getObject("actor_user_id", UUID.class),
                        resultSet.getTimestamp("occurred_at").toInstant(),
                        resultSet.getLong("resource_version")),
                ticketId,
                size,
                (long) page * size);
        return new TicketLifecyclePage(items, page, size, totalElements, totalPages(totalElements, size));
    }

    private boolean owns(UUID ticketId, UUID requesterUserId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM support.tickets WHERE id = ? AND requester_user_id = ?)",
                Boolean.class,
                ticketId,
                requesterUserId));
    }

    private boolean isAvailableToTechnician(UUID ticketId, UUID technicianUserId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM support.tickets
                    WHERE id = ?
                      AND status <> 'CLOSED'
                      AND (assignee_user_id IS NULL OR assignee_user_id = ?)
                )
                """,
                Boolean.class,
                ticketId,
                technicianUserId));
    }

    private boolean isAssignedToTechnician(UUID ticketId, UUID technicianUserId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM support.tickets
                    WHERE id = ?
                      AND status <> 'CLOSED'
                      AND assignee_user_id = ?
                )
                """,
                Boolean.class,
                ticketId,
                technicianUserId));
    }

    private boolean isAssignedToTechnicianIncludingClosed(UUID ticketId, UUID technicianUserId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM support.tickets
                    WHERE id = ? AND assignee_user_id = ?
                )
                """,
                Boolean.class,
                ticketId,
                technicianUserId));
    }

    private TicketResponse findAssignedForUpdate(UUID ticketId, UUID technicianUserId) {
        return jdbc.query(
                        """
                        SELECT id, type, summary, description, status, priority, source,
                               requester_user_id, created_by_user_id, assignee_user_id, asset_id,
                               created_at, updated_at, version
                        FROM support.tickets
                        WHERE id = ? AND assignee_user_id = ?
                        FOR UPDATE
                        """,
                        (resultSet, row) -> ticketResponse(resultSet),
                        ticketId,
                        technicianUserId)
                .stream()
                .findFirst()
                .orElseThrow(TicketNotFoundException::new);
    }

    private TicketResponse findReopenableForUpdate(UUID ticketId, UUID actorId) {
        return jdbc.query(
                        """
                        SELECT id, type, summary, description, status, priority, source,
                               requester_user_id, created_by_user_id, assignee_user_id, asset_id,
                               created_at, updated_at, version
                        FROM support.tickets
                        WHERE id = ? AND (requester_user_id = ? OR assignee_user_id = ?)
                        FOR UPDATE
                        """,
                        (resultSet, row) -> ticketResponse(resultSet),
                        ticketId,
                        actorId,
                        actorId)
                .stream()
                .findFirst()
                .orElseThrow(TicketNotFoundException::new);
    }

    private static void requireCurrentOperationalTicket(TicketResponse ticket, long expectedVersion) {
        if (ticket.version() != expectedVersion
                || ticket.status().equals("RESOLVED")
                || ticket.status().equals("CLOSED")) {
            throw new TicketMutationConflictException();
        }
    }

    private static void requireVersion(TicketResponse ticket, long expectedVersion) {
        if (ticket.version() != expectedVersion) {
            throw new TicketMutationConflictException();
        }
    }

    private static boolean validStatusTransition(String previous, String next) {
        return (previous.equals("OPEN") && next.equals("IN_PROGRESS"))
                || (previous.equals("IN_PROGRESS") && next.equals("WAITING_FOR_REQUESTER"))
                || (previous.equals("WAITING_FOR_REQUESTER") && next.equals("IN_PROGRESS"));
    }

    private void insertMutationEvent(
            String eventType,
            UUID actorId,
            UUID ticketId,
            long resourceVersion,
            UUID correlationId,
            String previousKey,
            String previousValue,
            String newKey,
            String newValue,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO support.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, '2.0', ?, 'SUPPORT',
                        'HUMAN_USER', ?, 'TICKET', ?, ?,
                        'SUCCESS', ?, jsonb_build_object(?::text, ?::text, ?::text, ?::text), ?)
                """,
                UUID.randomUUID(),
                eventType,
                Timestamp.from(now),
                actorId,
                ticketId,
                resourceVersion,
                correlationId,
                previousKey,
                previousValue,
                newKey,
                newValue,
                Timestamp.from(now));
    }

    private TicketResponse applyLifecycleTransition(
            TicketResponse ticket,
            UUID actorId,
            String lifecycleType,
            String newStatus,
            String resolutionSummary,
            String closeReason,
            String reopenReason,
            UUID correlationId) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        long newVersion = ticket.version() + 1;
        UUID lifecycleEventId = UUID.randomUUID();
        jdbc.update(
                "UPDATE support.tickets SET status = ?, updated_at = ?, version = ? WHERE id = ?",
                newStatus,
                Timestamp.from(now),
                newVersion,
                ticket.ticketId());
        jdbc.update(
                """
                INSERT INTO support.ticket_lifecycle_events
                    (id, ticket_id, event_type, from_status, to_status, actor_user_id,
                     resolution_summary, close_reason, reopen_reason, occurred_at, resource_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                lifecycleEventId,
                ticket.ticketId(),
                lifecycleType,
                ticket.status(),
                newStatus,
                actorId,
                resolutionSummary,
                closeReason,
                reopenReason,
                Timestamp.from(now),
                newVersion);
        insertLifecycleOutbox(
                "TICKET_" + lifecycleType,
                actorId,
                ticket.ticketId(),
                newVersion,
                correlationId,
                lifecycleEventId,
                ticket.status(),
                newStatus,
                closeReason,
                now);
        return findTicket(ticket.ticketId());
    }

    private void insertLifecycleOutbox(
            String eventType,
            UUID actorId,
            UUID ticketId,
            long resourceVersion,
            UUID correlationId,
            UUID lifecycleEventId,
            String fromStatus,
            String toStatus,
            String closeReason,
            Instant now) {
        String payloadExpression = closeReason == null
                ? "jsonb_build_object('lifecycleEventId', ?::text, 'fromStatus', ?::text, 'toStatus', ?::text)"
                : "jsonb_build_object('lifecycleEventId', ?::text, 'fromStatus', ?::text, 'toStatus', ?::text, 'closeReason', ?::text)";
        String sql = """
                INSERT INTO support.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, '2.0', ?, 'SUPPORT', 'HUMAN_USER', ?, 'TICKET', ?, ?,
                        'SUCCESS', ?, %s, ?)
                """.formatted(payloadExpression);
        if (closeReason == null) {
            jdbc.update(
                    sql,
                    UUID.randomUUID(),
                    eventType,
                    Timestamp.from(now),
                    actorId,
                    ticketId,
                    resourceVersion,
                    correlationId,
                    lifecycleEventId,
                    fromStatus,
                    toStatus,
                    Timestamp.from(now));
        } else {
            jdbc.update(
                    sql,
                    UUID.randomUUID(),
                    eventType,
                    Timestamp.from(now),
                    actorId,
                    ticketId,
                    resourceVersion,
                    correlationId,
                    lifecycleEventId,
                    fromStatus,
                    toStatus,
                    closeReason,
                    Timestamp.from(now));
        }
    }

    private TicketResponse findTicket(UUID ticketId) {
        return jdbc.query(
                        """
                        SELECT id, type, summary, description, status, priority, source,
                               requester_user_id, created_by_user_id, assignee_user_id, asset_id,
                               created_at, updated_at, version
                        FROM support.tickets WHERE id = ?
                        """,
                        (resultSet, row) -> ticketResponse(resultSet),
                        ticketId)
                .getFirst();
    }

    private static TicketCommentResponse ticketCommentResponse(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        return new TicketCommentResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("ticket_id", UUID.class),
                resultSet.getString("visibility"),
                resultSet.getObject("author_user_id", UUID.class),
                resultSet.getString("body"),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private static TicketResponse ticketResponse(java.sql.ResultSet resultSet) throws java.sql.SQLException {
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

    private static TicketListItemResponse ticketListItem(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        return new TicketListItemResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("type"),
                resultSet.getString("summary"),
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

    private record ClaimResult(String outcome, TicketResponse ticket) {}

    private record ConversationTicket(String status, UUID assigneeUserId, long version) {}
}

final class AssociatedAssetNotFoundException extends RuntimeException {}

final class InvalidCommentException extends RuntimeException {}

final class TicketNotFoundException extends RuntimeException {}

final class TicketClaimConflictException extends RuntimeException {}

final class TicketMutationConflictException extends RuntimeException {}
