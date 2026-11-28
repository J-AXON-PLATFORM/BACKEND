package com.jaxon.automation;

import java.sql.ResultSet;
import java.sql.SQLException;
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
class NotificationService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    NotificationService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    NotificationPage findOwn(UUID recipientUserId, String status, int page, int size) {
        String filter = status == null ? "" : " AND status = ? ";
        Object[] countArguments = status == null
                ? new Object[] {recipientUserId}
                : new Object[] {recipientUserId, status};
        long totalElements = jdbc.queryForObject(
                "SELECT count(*) FROM automation.notifications WHERE recipient_user_id = ?" + filter,
                Long.class,
                countArguments);
        Object[] pageArguments = status == null
                ? new Object[] {recipientUserId, size, (long) page * size}
                : new Object[] {recipientUserId, status, size, (long) page * size};
        List<NotificationResponse> items = jdbc.query(
                """
                SELECT notification_id, notification_type, resource_type, resource_id,
                       status, version, created_at, read_at
                FROM automation.notifications
                WHERE recipient_user_id = ?
                """ + filter + """
                ORDER BY created_at DESC, notification_id DESC
                LIMIT ? OFFSET ?
                """,
                (resultSet, row) -> response(resultSet),
                pageArguments);
        return new NotificationPage(
                items,
                page,
                size,
                totalElements,
                Math.toIntExact((totalElements + size - 1) / size));
    }

    @Transactional
    NotificationResponse markRead(UUID notificationId, UUID recipientUserId, long expectedVersion) {
        NotificationResponse current = jdbc.query(
                        """
                        SELECT notification_id, notification_type, resource_type, resource_id,
                               status, version, created_at, read_at
                        FROM automation.notifications
                        WHERE notification_id = ? AND recipient_user_id = ?
                        FOR UPDATE
                        """,
                        (resultSet, row) -> response(resultSet),
                        notificationId,
                        recipientUserId)
                .stream()
                .findFirst()
                .orElseThrow(NotificationNotFoundException::new);
        if (current.version() != expectedVersion) {
            throw new NotificationVersionConflictException();
        }
        if ("READ".equals(current.status())) {
            return current;
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        return jdbc.query(
                """
                UPDATE automation.notifications
                SET status = 'READ', version = version + 1, read_at = ?
                WHERE notification_id = ? AND recipient_user_id = ? AND status = 'UNREAD'
                RETURNING notification_id, notification_type, resource_type, resource_id,
                          status, version, created_at, read_at
                """,
                (resultSet, row) -> response(resultSet),
                Timestamp.from(now),
                notificationId,
                recipientUserId).getFirst();
    }

    private static NotificationResponse response(ResultSet resultSet) throws SQLException {
        Timestamp readAt = resultSet.getTimestamp("read_at");
        return new NotificationResponse(
                resultSet.getObject("notification_id", UUID.class),
                resultSet.getString("notification_type"),
                resultSet.getString("resource_type"),
                resultSet.getObject("resource_id", UUID.class),
                resultSet.getString("status"),
                resultSet.getLong("version"),
                resultSet.getTimestamp("created_at").toInstant(),
                readAt == null ? null : readAt.toInstant());
    }
}

final class NotificationNotFoundException extends RuntimeException {}

final class NotificationVersionConflictException extends RuntimeException {}
