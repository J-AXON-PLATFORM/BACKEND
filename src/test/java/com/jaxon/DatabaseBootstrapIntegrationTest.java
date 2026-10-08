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
            DockerImageName.parse("pgvector/pgvector:0.8.6-pg18-bookworm")
                    .asCompatibleSubstituteFor("postgres"));

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void bootstrapsPostgreSqlPgvectorAndExactlyTheApprovedSchemas() {
        String serverVersion = jdbcTemplate.queryForObject("SHOW server_version", String.class);
        String vectorVersion = jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'",
                String.class);
        Integer appliedMigrationCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM public.flyway_schema_history
                WHERE version = '20261006000000'
                  AND script = 'V20261006000000__bootstrap_schemas.sql'
                  AND success
                """,
                Integer.class);
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
        assertThat(appliedMigrationCount).isEqualTo(1);
        assertThat(modularSchemas).containsExactlyInAnyOrderElementsOf(EXPECTED_SCHEMAS);
        assertThat(modularTables).isEmpty();
        assertThat(publicTables).containsExactly("flyway_schema_history");
    }
}
