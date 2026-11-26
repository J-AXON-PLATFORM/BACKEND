package com.jaxon.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@SpringBootTest(properties = "jaxon.collector.audit-dispatch.enabled=false")
class CollectorAuditDispatchIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    CollectorAuditDispatcher dispatcher;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM collector.outbox_events");
        jdbc.update("DELETE FROM collector.agents");
    }

    @Test
    void materializesBothLifecycleFactsAndIgnoresHistoricalVersionOne() {
        UUID actorId = UUID.randomUUID();
        UUID pending = insertAgent("PENDING");
        UUID active = insertAgent("ACTIVE");
        UUID preRegistered = insertEvent(pending, actorId, "AGENT_PRE_REGISTERED", "2.0", 0);
        UUID activated = insertEvent(active, actorId, "AGENT_ACTIVATED", "2.0", 1);
        UUID historical = insertEvent(pending, actorId, "AGENT_PRE_REGISTERED", "1.0", 0);

        assertThat(dispatcher.dispatchDue(10))
                .isEqualTo(new CollectorAuditDispatcher.DispatchSummary(2, 0, 0, 0));
        assertAudit(preRegistered, "AGENT_PRE_REGISTERED", pending, 0L);
        assertAudit(activated, "AGENT_ACTIVATED", active, 1L);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM collector.outbox_deliveries WHERE event_id = ?",
                        Integer.class,
                        historical))
                .isZero();
    }

    @Test
    void concurrentDispatchersClaimEachFactOnce() throws Exception {
        UUID actorId = UUID.randomUUID();
        List<UUID> eventIds = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            UUID agentId = insertAgent("PENDING");
            eventIds.add(insertEvent(agentId, actorId, "AGENT_PRE_REGISTERED", "2.0", 0));
        }

        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(3);
        try {
            List<Future<CollectorAuditDispatcher.DispatchSummary>> futures = new ArrayList<>();
            for (int worker = 0; worker < 3; worker++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return dispatcher.dispatchDue(50);
                }));
            }
            start.countDown();
            int delivered = 0;
            for (Future<CollectorAuditDispatcher.DispatchSummary> future : futures) {
                CollectorAuditDispatcher.DispatchSummary summary = future.get();
                assertThat(summary.retried() + summary.failed()).isZero();
                delivered += summary.delivered();
            }
            assertThat(delivered).isEqualTo(12);
        } finally {
            executor.shutdownNow();
        }
        for (UUID eventId : eventIds) {
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM audit_reporting.audit_events WHERE source_event_id = ?",
                            Integer.class,
                            eventId))
                    .isEqualTo(1);
        }
    }

    private UUID insertAgent(String status) {
        UUID agentId = UUID.randomUUID();
        if ("ACTIVE".equals(status)) {
            jdbc.update(
                    """
                    INSERT INTO collector.agents
                        (id, asset_id, status, sftp_username, activated_at, created_at, updated_at, version)
                    VALUES (?, ?, 'ACTIVE', ?, now(), now(), now(), 1)
                    """,
                    agentId, UUID.randomUUID(), "agent-" + agentId);
        } else {
            jdbc.update(
                    """
                    INSERT INTO collector.agents
                        (id, asset_id, status, created_at, updated_at, version)
                    VALUES (?, ?, 'PENDING', now(), now(), 0)
                    """,
                    agentId, UUID.randomUUID());
        }
        return agentId;
    }

    private UUID insertEvent(
            UUID agentId, UUID actorId, String eventType, String eventVersion, long resourceVersion) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update(
                """
                INSERT INTO collector.outbox_events
                    (event_id, event_type, event_version, occurred_at, producer_module,
                     actor_type, actor_id, resource_type, resource_id, resource_version,
                     result, correlation_id, payload, next_attempt_at)
                VALUES (?, ?, ?, ?, 'COLLECTOR', 'HUMAN_USER', ?, 'AGENT', ?, ?,
                        'SUCCESS', ?, '{}'::jsonb, ?)
                """,
                eventId, eventType, eventVersion, Timestamp.from(now), actorId, agentId,
                resourceVersion, UUID.randomUUID(), Timestamp.from(now));
        return eventId;
    }

    private void assertAudit(UUID eventId, String eventType, UUID resourceId, long resourceVersion) {
        assertThat(jdbc.queryForMap(
                        """
                        SELECT event_type, event_version, producer_module, actor_type, resource_type,
                               resource_id, resource_version, result, metadata::text AS metadata
                        FROM audit_reporting.audit_events WHERE source_event_id = ?
                        """,
                        eventId))
                .containsEntry("event_type", eventType)
                .containsEntry("event_version", "2.0")
                .containsEntry("producer_module", "COLLECTOR")
                .containsEntry("actor_type", "HUMAN_USER")
                .containsEntry("resource_type", "AGENT")
                .containsEntry("resource_id", resourceId)
                .containsEntry("resource_version", resourceVersion)
                .containsEntry("result", "SUCCESS")
                .containsEntry("metadata", "{}");
        assertThat(delivery(eventId))
                .containsEntry("status", "DELIVERED")
                .containsEntry("attempts", 1);
    }

    private Map<String, Object> delivery(UUID eventId) {
        return jdbc.queryForMap(
                "SELECT * FROM collector.outbox_deliveries WHERE event_id = ? AND consumer = 'AUDIT'",
                eventId);
    }
}
