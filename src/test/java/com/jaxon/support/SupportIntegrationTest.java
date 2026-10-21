package com.jaxon.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "jaxon.identity.bootstrap.enabled=true",
            "jaxon.identity.bootstrap.username=Support.Operator",
            "jaxon.identity.bootstrap.password=Support test passphrase 42!"
        })
class SupportIntegrationTest {

    private static final String USERNAME = "Support.Operator";
    private static final String PASSWORD = "Support test passphrase 42!";
    private static final String TECHNICIAN_PASSWORD = "Technician test passphrase 42!";
    private static final String VALID_TICKET = """
            {"type":"INCIDENT","summary":"Printer unavailable","description":"The reception printer is offline."}
            """;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createsPersistsAndRetrievesAnOwnedTicketWithItsDurableEvent() throws Exception {
        Session session = login();
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> created = postTicket(session, VALID_TICKET, correlationId);

        assertThat(created.statusCode()).isEqualTo(201);
        UUID ticketId = UUID.fromString(extract(created.body(), "ticketId"));
        UUID requesterId = UUID.fromString(extract(created.body(), "requesterUserId"));
        assertThat(created.headers().firstValue("location")).contains("/api/v1/tickets/" + ticketId);
        assertThat(created.body())
                .contains("\"type\":\"INCIDENT\"")
                .contains("\"summary\":\"Printer unavailable\"")
                .contains("\"status\":\"OPEN\"")
                .contains("\"priority\":\"NORMAL\"")
                .contains("\"source\":\"WEB\"")
                .contains("\"createdByUserId\":\"" + requesterId + "\"")
                .contains("\"assigneeUserId\":null")
                .contains("\"assetId\":null")
                .contains("\"version\":0");
        assertThat(jdbc.queryForMap(
                        """
                        SELECT requester_user_id, created_by_user_id, assignee_user_id, status,
                               priority, source, asset_id
                        FROM support.tickets WHERE id = ?
                        """,
                        ticketId))
                .containsEntry("requester_user_id", requesterId)
                .containsEntry("created_by_user_id", requesterId)
                .containsEntry("assignee_user_id", null)
                .containsEntry("status", "OPEN")
                .containsEntry("priority", "NORMAL")
                .containsEntry("source", "WEB")
                .containsEntry("asset_id", null);
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, producer_module, actor_id, resource_id, correlation_id,
                               payload, status, attempts
                        FROM support.outbox_events WHERE resource_id = ?
                        """,
                        ticketId))
                .containsEntry("event_type", "TICKET_CREATED")
                .containsEntry("producer_module", "SUPPORT")
                .containsEntry("actor_id", requesterId)
                .containsEntry("resource_id", ticketId)
                .containsEntry("correlation_id", correlationId)
                .containsEntry("status", "PENDING")
                .containsEntry("attempts", 0);
        String payload = jdbc.queryForObject(
                "SELECT payload::text FROM support.outbox_events WHERE resource_id = ?", String.class, ticketId);
        assertThat(payload)
                .contains("\"type\": \"INCIDENT\"")
                .doesNotContain("Printer unavailable", "reception printer");

        HttpResponse<String> retrieved = getTicket(session.cookie(), ticketId.toString());
        assertThat(retrieved.statusCode()).isEqualTo(200);
        assertThat(retrieved.body()).isEqualTo(created.body());

        jdbc.update("UPDATE support.tickets SET requester_user_id = ? WHERE id = ?", UUID.randomUUID(), ticketId);
        HttpResponse<String> noLongerOwned = getTicket(session.cookie(), ticketId.toString());
        assertThat(noLongerOwned.statusCode()).isEqualTo(404);
        assertThat(noLongerOwned.body()).contains("\"code\":\"TICKET_NOT_FOUND\"");
    }

    @Test
    void validatesAnOptionalAssetThroughInventoryWithoutCrossSchemaReferences() throws Exception {
        Session session = login();
        UUID assetId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO inventory.assets (id, name, asset_type, status, created_at, updated_at, version)
                VALUES (?, 'Associated asset', 'DESKTOP', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                assetId);

        HttpResponse<String> created = postTicket(
                session,
                """
                {"type":"SERVICE_REQUEST","summary":"Install software","description":"Approved tool.","assetId":"%s"}
                """.formatted(assetId),
                UUID.randomUUID());
        assertThat(created.statusCode()).isEqualTo(201);
        assertThat(created.body()).contains("\"assetId\":\"" + assetId + "\"");

        int ticketsBefore = jdbc.queryForObject("SELECT count(*) FROM support.tickets", Integer.class);
        int eventsBefore = jdbc.queryForObject("SELECT count(*) FROM support.outbox_events", Integer.class);
        HttpResponse<String> missing = postTicket(
                session,
                """
                {"type":"QUESTION","summary":"Unknown asset","description":"Cannot associate it.","assetId":"%s"}
                """.formatted(UUID.randomUUID()),
                UUID.randomUUID());
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.body()).contains("\"code\":\"ASSET_NOT_FOUND\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.tickets", Integer.class))
                .isEqualTo(ticketsBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM support.outbox_events", Integer.class))
                .isEqualTo(eventsBefore);
    }

    @Test
    void rejectsInvalidFieldsAndUnavailableTicketIdentifiersSafely() throws Exception {
        Session session = login();
        HttpResponse<String> invalid = postTicket(
                session,
                "{\"type\":\"INVALID\",\"summary\":\" \",\"description\":\" \"}",
                UUID.randomUUID());
        assertThat(invalid.statusCode()).isEqualTo(422);
        assertThat(invalid.headers().firstValue("content-type")).hasValueSatisfying(
                value -> assertThat(value).startsWith("application/problem+json"));
        assertThat(invalid.body())
                .contains("\"code\":\"VALIDATION_FAILED\"")
                .contains("\"pointer\":\"/type\"")
                .contains("\"pointer\":\"/summary\"")
                .contains("\"pointer\":\"/description\"");
        assertThat(getTicket(session.cookie(), "not-a-uuid").body())
                .contains("\"code\":\"TICKET_NOT_FOUND\"");
        assertThat(getTicket(session.cookie(), UUID.randomUUID().toString()).statusCode()).isEqualTo(404);
    }

    @Test
    void requiresAuthenticationCsrfAndTheExplicitItOperatorRole() throws Exception {
        assertThat(getTicket(null, UUID.randomUUID().toString()).statusCode()).isEqualTo(401);
        assertThat(getTickets(null, "0", "20").statusCode()).isEqualTo(401);
        assertThat(getComments(null, UUID.randomUUID().toString(), "0", "50").statusCode()).isEqualTo(401);
        Session session = login();
        HttpResponse<String> missingCsrf = postTicket(
                new Session(session.cookie(), null), VALID_TICKET, UUID.randomUUID());
        assertThat(missingCsrf.statusCode()).isEqualTo(403);
        assertThat(missingCsrf.body()).contains("\"code\":\"CSRF_INVALID\"");
        UUID ticketId = UUID.fromString(extract(postTicket(session, VALID_TICKET, UUID.randomUUID()).body(), "ticketId"));
        HttpResponse<String> commentMissingCsrf = postComment(
                new Session(session.cookie(), null),
                ticketId.toString(),
                "{\"body\":\"Denied\"}",
                UUID.randomUUID());
        assertThat(commentMissingCsrf.statusCode()).isEqualTo(403);
        assertThat(commentMissingCsrf.body()).contains("\"code\":\"CSRF_INVALID\"");

        jdbc.update("DELETE FROM identity.human_user_roles WHERE role_code = 'IT_OPERATOR'");
        try {
            assertThat(getTicket(session.cookie(), UUID.randomUUID().toString()).statusCode()).isEqualTo(403);
            assertThat(postTicket(session, VALID_TICKET, UUID.randomUUID()).statusCode()).isEqualTo(403);
            assertThat(getTickets(session.cookie(), "0", "20").statusCode()).isEqualTo(403);
            assertThat(getComments(session.cookie(), UUID.randomUUID().toString(), "0", "50").statusCode())
                    .isEqualTo(403);
            assertThat(postComment(session, UUID.randomUUID().toString(), "{\"body\":\"Denied\"}", UUID.randomUUID())
                            .statusCode())
                    .isEqualTo(403);
        } finally {
            jdbc.update(
                    """
                    INSERT INTO identity.human_user_roles (user_id, role_code)
                    SELECT id, 'IT_OPERATOR' FROM identity.human_users
                    WHERE normalized_username = 'support.operator'
                    ON CONFLICT DO NOTHING
                    """);
        }
    }

    @Test
    void listsOnlyOwnedTicketsWithStablePagination() throws Exception {
        Session session = login();
        UUID first = UUID.fromString(extract(postTicket(session, VALID_TICKET, UUID.randomUUID()).body(), "ticketId"));
        UUID second = UUID.fromString(extract(
                postTicket(
                                session,
                                """
                                {"type":"QUESTION","summary":"Second ticket","description":"Second description."}
                                """,
                                UUID.randomUUID())
                        .body(),
                "ticketId"));
        UUID hidden = UUID.fromString(extract(
                postTicket(
                                session,
                                """
                                {"type":"OTHER","summary":"Hidden ticket","description":"Another requester."}
                                """,
                                UUID.randomUUID())
                        .body(),
                "ticketId"));
        jdbc.update("UPDATE support.tickets SET requester_user_id = ? WHERE id = ?", UUID.randomUUID(), hidden);
        UUID requesterId = jdbc.queryForObject(
                "SELECT requester_user_id FROM support.tickets WHERE id = ?", UUID.class, first);
        jdbc.update(
                "UPDATE support.tickets SET requester_user_id = ? WHERE requester_user_id = ? AND id NOT IN (?, ?)",
                UUID.randomUUID(),
                requesterId,
                first,
                second);
        jdbc.update(
                "UPDATE support.tickets SET created_at = '2026-10-20T10:00:00Z', updated_at = '2026-10-20T10:00:00Z' WHERE id IN (?, ?)",
                first,
                second);

        HttpResponse<String> firstPage = getTickets(session.cookie(), "0", "1");
        HttpResponse<String> secondPage = getTickets(session.cookie(), "1", "1");

        assertThat(firstPage.statusCode()).isEqualTo(200);
        assertThat(firstPage.body())
                .contains("\"page\":0", "\"size\":1", "\"totalElements\":2", "\"totalPages\":2")
                .doesNotContain(hidden.toString(), "\"description\"", "The reception printer is offline.",
                        "Second description.");
        assertThat(secondPage.body())
                .contains("\"page\":1", "\"size\":1", "\"totalElements\":2", "\"totalPages\":2")
                .doesNotContain(hidden.toString(), "\"description\"", "The reception printer is offline.",
                        "Second description.");
        UUID expectedFirst = first.toString().compareTo(second.toString()) > 0 ? first : second;
        UUID expectedSecond = first.toString().compareTo(second.toString()) > 0 ? second : first;
        assertThat(firstPage.body()).contains(expectedFirst.toString()).doesNotContain(expectedSecond.toString());
        assertThat(secondPage.body()).contains(expectedSecond.toString()).doesNotContain(expectedFirst.toString());
        assertThat(getTicket(session.cookie(), first.toString()).body())
                .contains("\"description\":\"The reception printer is offline.\"");
    }

    @Test
    void appendsAndPagesPublicCommentsWithAContentFreeDurableEvent() throws Exception {
        Session session = login();
        UUID ticketId = UUID.fromString(extract(postTicket(session, VALID_TICKET, UUID.randomUUID()).body(), "ticketId"));
        UUID correlationId = UUID.randomUUID();

        HttpResponse<String> created = postComment(
                session, ticketId.toString(), "{\"body\":\"  Public update  \"}", correlationId);

        assertThat(created.statusCode()).isEqualTo(201);
        UUID commentId = UUID.fromString(extract(created.body(), "commentId"));
        assertThat(created.body())
                .contains("\"ticketId\":\"" + ticketId + "\"")
                .contains("\"visibility\":\"PUBLIC\"")
                .contains("\"body\":\"Public update\"");
        assertThat(jdbc.queryForMap(
                        """
                        SELECT ticket_id, visibility, author_user_id, body
                        FROM support.ticket_comments WHERE id = ?
                        """,
                        commentId))
                .containsEntry("ticket_id", ticketId)
                .containsEntry("visibility", "PUBLIC")
                .containsEntry("body", "Public update");
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, resource_type, resource_id, correlation_id, payload
                        FROM support.outbox_events WHERE event_type = 'TICKET_COMMENT_ADDED' AND resource_id = ?
                        """,
                        ticketId))
                .containsEntry("event_type", "TICKET_COMMENT_ADDED")
                .containsEntry("resource_type", "TICKET")
                .containsEntry("resource_id", ticketId)
                .containsEntry("correlation_id", correlationId);
        String payload = jdbc.queryForObject(
                """
                SELECT payload::text FROM support.outbox_events
                WHERE event_type = 'TICKET_COMMENT_ADDED' AND resource_id = ?
                """,
                String.class,
                ticketId);
        assertThat(payload)
                .contains(commentId.toString(), "\"visibility\": \"PUBLIC\"")
                .doesNotContain("Public update", "Printer unavailable", "reception printer");

        HttpResponse<String> comments = getComments(session.cookie(), ticketId.toString(), "0", "1");
        assertThat(comments.statusCode()).isEqualTo(200);
        assertThat(comments.body())
                .contains(commentId.toString(), "\"totalElements\":1", "\"totalPages\":1");
    }

    @Test
    void concealsForeignTicketsAndValidatesCommentAndPageBoundaries() throws Exception {
        Session session = login();
        UUID ticketId = UUID.fromString(extract(postTicket(session, VALID_TICKET, UUID.randomUUID()).body(), "ticketId"));
        jdbc.update("UPDATE support.tickets SET requester_user_id = ? WHERE id = ?", UUID.randomUUID(), ticketId);

        assertThat(getComments(session.cookie(), ticketId.toString(), "0", "50").body())
                .contains("\"code\":\"TICKET_NOT_FOUND\"");
        assertThat(postComment(
                                session, ticketId.toString(), "{\"body\":\"Cannot append\"}", UUID.randomUUID())
                        .body())
                .contains("\"code\":\"TICKET_NOT_FOUND\"");
        assertThat(getComments(session.cookie(), UUID.randomUUID().toString(), "0", "50").body())
                .contains("\"code\":\"TICKET_NOT_FOUND\"");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM support.ticket_comments WHERE ticket_id = ?", Integer.class, ticketId))
                .isZero();

        assertThat(getTickets(session.cookie(), "-1", "20").body())
                .contains("\"code\":\"VALIDATION_FAILED\"");
        assertThat(getTickets(session.cookie(), "0", "51").statusCode()).isEqualTo(422);
        assertThat(getComments(session.cookie(), UUID.randomUUID().toString(), "0", "101").statusCode())
                .isEqualTo(422);

        UUID ownTicket = UUID.fromString(extract(postTicket(session, VALID_TICKET, UUID.randomUUID()).body(), "ticketId"));
        assertThat(postComment(session, ownTicket.toString(), "{\"body\":\"   \"}", UUID.randomUUID()).body())
                .contains("\"pointer\":\"/body\"");
        String tooLong = "x".repeat(10_001);
        assertThat(postComment(
                                session,
                                ownTicket.toString(),
                                "{\"body\":\"" + tooLong + "\"}",
                                UUID.randomUUID())
                        .statusCode())
                .isEqualTo(422);
        String padded = " ".repeat(10_000) + "x";
        assertThat(postComment(
                                session,
                                ownTicket.toString(),
                                "{\"body\":\"" + padded + "\"}",
                                UUID.randomUUID())
                        .statusCode())
                .isEqualTo(422);
    }

    @Test
    void rollsBackTheCommentWhenItsOutboxEventCannotBePersisted() throws Exception {
        Session session = login();
        UUID ticketId = UUID.fromString(extract(postTicket(session, VALID_TICKET, UUID.randomUUID()).body(), "ticketId"));
        jdbc.execute(
                """
                ALTER TABLE support.outbox_events
                ADD CONSTRAINT support_test_reject_comment_event
                CHECK (event_type <> 'TICKET_COMMENT_ADDED') NOT VALID
                """);
        try {
            HttpResponse<String> response = postComment(
                    session, ticketId.toString(), "{\"body\":\"Must roll back\"}", UUID.randomUUID());
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM support.ticket_comments WHERE ticket_id = ?", Integer.class, ticketId))
                    .isZero();
        } finally {
            jdbc.execute("ALTER TABLE support.outbox_events DROP CONSTRAINT support_test_reject_comment_event");
        }
    }

    @Test
    void exposesTheTechnicianQueueAndClaimsExactlyOnceUnderConcurrency() throws Exception {
        Session operator = login();
        String firstUsername = "Technician.One." + UUID.randomUUID();
        String secondUsername = "Technician.Two." + UUID.randomUUID();
        Session firstTechnician = createAndLoginTechnician(operator, firstUsername);
        Session secondTechnician = createAndLoginTechnician(operator, secondUsername);
        UUID firstTechnicianId = userId(firstUsername);
        UUID secondTechnicianId = userId(secondUsername);
        jdbc.update("UPDATE support.tickets SET status = 'CLOSED'");
        UUID ticketId = UUID.fromString(extract(
                postTicket(operator, VALID_TICKET, UUID.randomUUID()).body(), "ticketId"));
        postComment(operator, ticketId.toString(), "{\"body\":\"Requester update\"}", UUID.randomUUID());

        HttpResponse<String> queue = getTechnicianQueue(firstTechnician.cookie(), "0", "20");
        assertThat(queue.statusCode()).isEqualTo(200);
        assertThat(queue.body())
                .contains(ticketId.toString(), "\"totalElements\":1", "\"version\":0")
                .doesNotContain("\"description\"", "reception printer");
        assertThat(getTechnicianTicket(firstTechnician.cookie(), ticketId.toString()).body())
                .contains("\"description\":\"The reception printer is offline.\"");
        assertThat(getTechnicianComments(firstTechnician.cookie(), ticketId.toString(), "0", "50").body())
                .contains("Requester update", "\"visibility\":\"PUBLIC\"");

        UUID firstCorrelation = UUID.randomUUID();
        UUID secondCorrelation = UUID.randomUUID();
        CompletableFuture<HttpResponse<String>> firstClaim = claimAsync(
                firstTechnician, ticketId, 0, firstCorrelation);
        CompletableFuture<HttpResponse<String>> secondClaim = claimAsync(
                secondTechnician, ticketId, 0, secondCorrelation);
        List<HttpResponse<String>> responses = List.of(firstClaim.join(), secondClaim.join());
        assertThat(responses).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(200, 409);

        HttpResponse<String> winner = responses.stream()
                .filter(response -> response.statusCode() == 200)
                .findFirst()
                .orElseThrow();
        UUID winnerId = UUID.fromString(extract(winner.body(), "assigneeUserId"));
        Session winnerSession = winnerId.equals(firstTechnicianId) ? firstTechnician : secondTechnician;
        Session loserSession = winnerId.equals(firstTechnicianId) ? secondTechnician : firstTechnician;
        UUID loserId = winnerId.equals(firstTechnicianId) ? secondTechnicianId : firstTechnicianId;
        assertThat(winner.body()).contains("\"version\":1", "\"status\":\"OPEN\"");
        assertThat(jdbc.queryForMap(
                        "SELECT assignee_user_id, version, status FROM support.tickets WHERE id = ?", ticketId))
                .containsEntry("assignee_user_id", winnerId)
                .containsEntry("version", 1L)
                .containsEntry("status", "OPEN");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM support.outbox_events WHERE resource_id = ? AND event_type = 'TICKET_ASSIGNED'",
                        Integer.class,
                        ticketId))
                .isOne();
        assertThat(jdbc.queryForMap(
                        """
                        SELECT actor_id, resource_version, correlation_id, payload
                        FROM support.outbox_events
                        WHERE resource_id = ? AND event_type = 'TICKET_ASSIGNED'
                        """,
                        ticketId))
                .containsEntry("actor_id", winnerId)
                .containsEntry("resource_version", 1L)
                .containsEntry(
                        "correlation_id",
                        winnerId.equals(firstTechnicianId) ? firstCorrelation : secondCorrelation);
        String payload = jdbc.queryForObject(
                """
                SELECT payload::text FROM support.outbox_events
                WHERE resource_id = ? AND event_type = 'TICKET_ASSIGNED'
                """,
                String.class,
                ticketId);
        assertThat(payload)
                .contains("\"assigneeUserId\": \"" + winnerId + "\"")
                .doesNotContain("summary", "description", "comment");

        HttpResponse<String> idempotent = claim(winnerSession, ticketId, 1, UUID.randomUUID());
        assertThat(idempotent.statusCode()).isEqualTo(200);
        assertThat(idempotent.body()).isEqualTo(winner.body());
        assertThat(claim(winnerSession, ticketId, 0, UUID.randomUUID()).statusCode()).isEqualTo(409);
        assertThat(claim(loserSession, ticketId, 1, UUID.randomUUID()).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM support.outbox_events WHERE resource_id = ? AND event_type = 'TICKET_ASSIGNED'",
                        Integer.class,
                        ticketId))
                .isOne();
        assertThat(getTechnicianQueue(winnerSession.cookie(), "0", "20").body())
                .contains(ticketId.toString(), winnerId.toString());
        assertThat(getTechnicianQueue(loserSession.cookie(), "0", "20").body())
                .doesNotContain(ticketId.toString(), loserId.toString());
        assertThat(getTechnicianTicket(loserSession.cookie(), ticketId.toString()).statusCode()).isEqualTo(404);
    }

    @Test
    void protectsClaimAndRollsItBackWhenTheOutboxWriteFails() throws Exception {
        Session operator = login();
        String username = "Technician.Rollback." + UUID.randomUUID();
        Session technician = createAndLoginTechnician(operator, username);
        UUID ticketId = UUID.fromString(extract(
                postTicket(operator, VALID_TICKET, UUID.randomUUID()).body(), "ticketId"));

        assertThat(getTechnicianQueue(null, "0", "20").statusCode()).isEqualTo(401);
        assertThat(getTechnicianQueue(operator.cookie(), "0", "20").statusCode()).isEqualTo(403);
        assertThat(claim(operator, ticketId, 0, UUID.randomUUID()).statusCode()).isEqualTo(403);
        assertThat(claim(new Session(technician.cookie(), null), ticketId, 0, UUID.randomUUID()).body())
                .contains("\"code\":\"CSRF_INVALID\"");
        assertThat(claimRaw(technician, ticketId.toString(), "{}", UUID.randomUUID()).statusCode()).isEqualTo(422);
        assertThat(claimRaw(technician, ticketId.toString(), "{\"expectedVersion\":-1}", UUID.randomUUID())
                        .statusCode())
                .isEqualTo(422);
        assertThat(claim(technician, UUID.randomUUID(), 0, UUID.randomUUID()).statusCode()).isEqualTo(404);

        jdbc.execute(
                """
                ALTER TABLE support.outbox_events
                ADD CONSTRAINT support_test_reject_assignment_event
                CHECK (event_type <> 'TICKET_ASSIGNED') NOT VALID
                """);
        try {
            assertThat(claim(technician, ticketId, 0, UUID.randomUUID()).statusCode()).isEqualTo(503);
            assertThat(jdbc.queryForMap(
                            "SELECT assignee_user_id, version FROM support.tickets WHERE id = ?", ticketId))
                    .containsEntry("assignee_user_id", null)
                    .containsEntry("version", 0L);
        } finally {
            jdbc.execute("ALTER TABLE support.outbox_events DROP CONSTRAINT support_test_reject_assignment_event");
        }
    }

    private Session login() throws Exception {
        return login(USERNAME, PASSWORD);
    }

    private Session login(String username, String password) throws Exception {
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(uri("/api/v1/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String setCookie = response.headers().firstValue("set-cookie").orElseThrow();
        return new Session(setCookie.substring(0, setCookie.indexOf(';')), extract(response.body(), "csrfToken"));
    }

    private Session createAndLoginTechnician(Session administrator, String username) throws Exception {
        HttpResponse<String> created = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(uri("/api/v1/users"))
                        .header("Cookie", administrator.cookie())
                        .header("Content-Type", "application/json")
                        .header("X-CSRF-Token", administrator.csrfToken())
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"username":"%s","password":"%s","roles":["SUPPORT_TECHNICIAN"]}
                                """.formatted(username, TECHNICIAN_PASSWORD)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(created.statusCode()).isEqualTo(201);
        return login(username, TECHNICIAN_PASSWORD);
    }

    private UUID userId(String username) {
        return jdbc.queryForObject(
                "SELECT id FROM identity.human_users WHERE normalized_username = ?",
                UUID.class,
                username.toLowerCase());
    }

    private HttpResponse<String> postTicket(Session session, String body, UUID correlationId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/tickets"))
                .header("Cookie", session.cookie())
                .header("Content-Type", "application/json")
                .header("X-Correlation-ID", correlationId.toString())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (session.csrfToken() != null) {
            request.header("X-CSRF-Token", session.csrfToken());
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTicket(String cookie, String ticketId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(uri("/api/v1/tickets/" + ticketId)).GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTickets(String cookie, String page, String size) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/tickets?page=" + page + "&size=" + size))
                .GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTechnicianQueue(String cookie, String page, String size) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/tickets/technician-queue?page=" + page + "&size=" + size))
                .GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTechnicianTicket(String cookie, String ticketId) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/tickets/technician-queue/" + ticketId))
                .GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getTechnicianComments(String cookie, String ticketId, String page, String size)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/tickets/technician-queue/" + ticketId + "/comments?page=" + page + "&size=" + size))
                .GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> claim(Session session, UUID ticketId, long expectedVersion, UUID correlationId)
            throws Exception {
        return claimRaw(
                session,
                ticketId.toString(),
                "{\"expectedVersion\":" + expectedVersion + "}",
                correlationId);
    }

    private CompletableFuture<HttpResponse<String>> claimAsync(
            Session session, UUID ticketId, long expectedVersion, UUID correlationId) {
        return httpClient.sendAsync(
                claimRequest(
                        session,
                        ticketId.toString(),
                        "{\"expectedVersion\":" + expectedVersion + "}",
                        correlationId),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> claimRaw(Session session, String ticketId, String body, UUID correlationId)
            throws Exception {
        return httpClient.send(
                claimRequest(session, ticketId, body, correlationId), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest claimRequest(Session session, String ticketId, String body, UUID correlationId) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/tickets/" + ticketId + "/claim"))
                .header("Cookie", session.cookie())
                .header("Content-Type", "application/json")
                .header("X-Correlation-ID", correlationId.toString())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (session.csrfToken() != null) {
            request.header("X-CSRF-Token", session.csrfToken());
        }
        return request.build();
    }

    private HttpResponse<String> postComment(Session session, String ticketId, String body, UUID correlationId)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/tickets/" + ticketId + "/comments"))
                .header("Cookie", session.cookie())
                .header("Content-Type", "application/json")
                .header("X-Correlation-ID", correlationId.toString())
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (session.csrfToken() != null) {
            request.header("X-CSRF-Token", session.csrfToken());
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getComments(String cookie, String ticketId, String page, String size)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(uri("/api/v1/tickets/" + ticketId + "/comments?page=" + page + "&size=" + size))
                .GET();
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private static String extract(String body, String property) {
        var matcher = Pattern.compile("\\\"" + property + "\\\":\\\"([^\\\"]+)\\\"").matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private record Session(String cookie, String csrfToken) {}
}
