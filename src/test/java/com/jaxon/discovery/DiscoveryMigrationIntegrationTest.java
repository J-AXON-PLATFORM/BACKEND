package com.jaxon.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class DiscoveryMigrationIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @Test
    void upgradesHistoricalDevicesAndAllDiscoveryOutboxVariants() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("20261028000000")
                .load()
                .migrate();
        JdbcTemplate jdbc = new JdbcTemplate(
                new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        Instant now = Instant.parse("2026-10-28T12:00:00Z");
        UUID actorId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        UUID executorId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID ingestionId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO discovery.authorized_scopes
                    (id,name,normalized_name,cidr,status,created_by_user_id,created_at,updated_at,version)
                VALUES (?, 'Historical scope', ?, '192.0.2.0/24', 'ACTIVE', ?, ?, ?, 0)
                """,
                scopeId, "historical-" + scopeId, actorId, Timestamp.from(now), Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO discovery.discovery_executors
                    (id,name,normalized_name,status,created_by_user_id,created_at,updated_at,version,
                     sftp_username,activated_at)
                VALUES (?, 'Historical executor', ?, 'ACTIVE', ?, ?, ?, 1, ?, ?)
                """,
                executorId, "historical-" + executorId, actorId, Timestamp.from(now), Timestamp.from(now),
                "discovery-executor-" + executorId, Timestamp.from(now));
        jdbc.update(
                "INSERT INTO discovery.discovery_executor_scopes (executor_id,scope_id,associated_at) VALUES (?,?,?)",
                executorId, scopeId, Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO discovery.discovery_jobs
                    (job_id,executor_id,scope_id,profile,status,actor_id,correlation_id,created_at,
                     expires_at,dispatched_at,updated_at,version,started_at,completed_at,failure_code,
                     result_checked_at,targets_considered,targets_scanned,observations_found)
                VALUES (?,?,?,'BASIC','SUCCEEDED',?,?,?,?,?,?,2,?,?,NULL,?,1,1,1)
                """,
                jobId, executorId, scopeId, actorId, UUID.randomUUID(), Timestamp.from(now),
                Timestamp.from(now.plusSeconds(3600)), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now), Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO discovery.discovery_result_ingestions
                    (ingestion_id,path_executor_id,path_job_id,source_path,file_fingerprint,file_size,
                     received_at,status,reason_code,claimed_result_id,claimed_job_id)
                VALUES (?,?,?,?,'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',100,
                        ?,'ACCEPTED',NULL,?,?)
                """,
                ingestionId, executorId, jobId, "mailbox/" + jobId + ".json", Timestamp.from(now),
                resultId, jobId);
        jdbc.update(
                """
                INSERT INTO discovery.accepted_discovery_results
                    (result_id,job_id,ingestion_id,executor_id,scope_id,schema_version,profile,result_status,
                     failure_code,started_at,completed_at,received_at,content_sha256,content_size,
                     targets_considered,targets_scanned,observations_found)
                VALUES (?,?,?, ?,?,'1.0','BASIC','SUCCEEDED',NULL,?,?,?,
                        'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',100,1,1,1)
                """,
                resultId, jobId, ingestionId, executorId, scopeId, Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO discovery.discovered_devices
                    (discovered_device_id,result_id,job_id,result_status,observation_index,status,
                     ip_address,mac_address,hostname,evidence,open_tcp_ports,observed_at,created_at)
                VALUES (?,?,?,'SUCCEEDED',0,'UNREVIEWED','192.0.2.10',NULL,NULL,
                        ARRAY['TCP']::varchar[],ARRAY[443],?,?)
                """,
                deviceId, resultId, jobId, Timestamp.from(now), Timestamp.from(now));

        insertEvent(jdbc, "DISCOVERY_SCOPE_CREATED", "HUMAN_USER", "AUTHORIZED_SCOPE", 0,
                "{\"status\":\"ACTIVE\"}", actorId, now);
        insertEvent(jdbc, "DISCOVERY_SCOPE_STATUS_CHANGED", "HUMAN_USER", "AUTHORIZED_SCOPE", 1,
                "{\"previousStatus\":\"ACTIVE\",\"newStatus\":\"DISABLED\"}", actorId, now);
        insertEvent(jdbc, "DISCOVERY_EXECUTOR_PRE_REGISTERED", "HUMAN_USER", "DISCOVERY_EXECUTOR", 0,
                "{\"status\":\"PENDING\"}", actorId, now);
        insertEvent(jdbc, "DISCOVERY_EXECUTOR_ACTIVATED", "HUMAN_USER", "DISCOVERY_EXECUTOR", 1,
                "{\"status\":\"ACTIVE\"}", actorId, now);
        insertEvent(jdbc, "DISCOVERY_JOB_CREATED", "HUMAN_USER", "DISCOVERY_JOB", 0,
                "{\"status\":\"PENDING\",\"profile\":\"BASIC\"}", actorId, now);
        insertEvent(jdbc, "DISCOVERY_JOB_DISPATCHED", "HUMAN_USER", "DISCOVERY_JOB", 1,
                "{\"status\":\"DISPATCHED\"}", actorId, now);
        insertEvent(jdbc, "DISCOVERY_JOB_COMPLETED", "DISCOVERY_EXECUTOR", "DISCOVERY_JOB", 2,
                "{\"status\":\"SUCCEEDED\",\"observationsFound\":1}", executorId, now);

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(jdbc.queryForMap(
                        "SELECT status, version, reviewed_at, reviewed_by_user_id "
                                + "FROM discovery.discovered_devices WHERE discovered_device_id = ?",
                        deviceId))
                .containsEntry("status", "UNREVIEWED")
                .containsEntry("version", 0L)
                .containsEntry("reviewed_at", null)
                .containsEntry("reviewed_by_user_id", null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM discovery.outbox_events", Integer.class))
                .isEqualTo(7);
        assertThat(jdbc.queryForList(
                        """
                        SELECT conname FROM pg_constraint
                        WHERE conrelid IN (
                            'discovery.discovered_devices'::regclass,
                            'discovery.outbox_events'::regclass)
                          AND convalidated
                        """,
                        String.class))
                .contains(
                        "discovered_devices_status_check",
                        "discovered_devices_review_check",
                        "outbox_events_event_type_check",
                        "outbox_events_resource_type_check",
                        "outbox_events_payload_check");
        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE discovery.discovered_devices SET status = 'IGNORED' WHERE discovered_device_id = ?",
                        deviceId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void insertEvent(
            JdbcTemplate jdbc,
            String eventType,
            String actorType,
            String resourceType,
            long resourceVersion,
            String payload,
            UUID actorId,
            Instant now) {
        jdbc.update(
                """
                INSERT INTO discovery.outbox_events
                    (event_id,event_type,event_version,occurred_at,producer_module,actor_type,actor_id,
                     resource_type,resource_id,resource_version,result,correlation_id,payload,next_attempt_at)
                VALUES (?,?,'1.0',?,'DISCOVERY',?,?,?, ?,?,'SUCCESS',?,?::jsonb,?)
                """,
                UUID.randomUUID(), eventType, Timestamp.from(now), actorType, actorId, resourceType,
                UUID.randomUUID(), resourceVersion, UUID.randomUUID(), payload, Timestamp.from(now));
    }
}
