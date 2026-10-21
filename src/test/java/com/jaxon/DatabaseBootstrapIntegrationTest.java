package com.jaxon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

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
@SpringBootTest
class DatabaseBootstrapIntegrationTest {

    private static final Set<String> EXPECTED_SCHEMAS = Set.of(
            "inventory",
            "discovery",
            "collector",
            "support",
            "ai",
            "identity",
            "automation",
            "audit_reporting");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("jaxon-postgres-pgvector-test:18.6-0.8.6")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void bootstrapsPostgreSqlPgvectorAndExactlyTheApprovedSchemas() {
        String serverVersion = jdbcTemplate.queryForObject("SHOW server_version", String.class);
        String vectorVersion = jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
                String.class);
        List<String> appliedMigrations = jdbcTemplate.queryForList(
                """
                SELECT version
                FROM public.flyway_schema_history
                WHERE success
                ORDER BY installed_rank
                """,
                String.class);
        List<String> modularSchemas = jdbcTemplate.queryForList(
                """
                SELECT schema_name
                FROM information_schema.schemata
                WHERE schema_name <> 'public'
                  AND schema_name <> 'information_schema'
                  AND schema_name NOT LIKE 'pg_%'
                ORDER BY schema_name
                """,
                String.class);
        List<String> modularTables = jdbcTemplate.queryForList(
                """
                SELECT table_schema || '.' || table_name
                FROM information_schema.tables
                WHERE table_schema IN ('inventory', 'discovery', 'collector', 'support',
                                       'ai', 'identity', 'automation', 'audit_reporting')
                  AND table_type = 'BASE TABLE'
                ORDER BY table_schema, table_name
                """,
                String.class);
        List<String> publicTables = jdbcTemplate.queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """,
                String.class);

        assertThat(serverVersion).startsWith("18.");
        assertThat(vectorVersion).isEqualTo("0.8.6");
        assertThat(appliedMigrations)
                .contains(
                        "20261006000000",
                        "20261010000000",
                        "20261011000000",
                        "20261011010000",
                        "20261012000000",
                        "20261016000000",
                        "20261018000000",
                        "20261019000000",
                        "20261020000000",
                        "20261020010000",
                        "20261021000000");
        assertThat(modularSchemas).containsExactlyInAnyOrderElementsOf(EXPECTED_SCHEMAS);
        assertThat(modularTables).containsExactly(
                "collector.accepted_snapshots",
                "collector.accepted_task_results",
                "collector.agent_tasks",
                "collector.agents",
                "collector.outbox_events",
                "collector.snapshot_ingestions",
                "collector.task_result_ingestions",
                "identity.human_sessions",
                "identity.human_user_roles",
                "identity.human_users",
                "identity.login_attempts",
                "identity.outbox_events",
                "identity.roles",
                "identity.technical_identities",
                "inventory.asset_types",
                "inventory.assets",
                "inventory.outbox_events",
                "support.outbox_events",
                "support.ticket_comments",
                "support.tickets");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM identity.human_users", Integer.class))
                .isZero();
        assertThat(publicTables).containsExactly("flyway_schema_history");
    }
}
