package com.jaxon.audit_reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AuditOwnershipTest {

    private static final Pattern FOREIGN_SCHEMA = Pattern.compile(
            "\\b(inventory|discovery|collector|support|ai|identity|automation)\\.[a-z_]+",
            Pattern.CASE_INSENSITIVE);

    @Test
    void auditReportingSqlReferencesOnlyItsOwnSchema() throws IOException {
        List<Path> sources;
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/jaxon/audit_reporting"))) {
            sources = files.filter(path -> path.toString().endsWith(".java")).toList();
        }
        assertThat(sources).isNotEmpty();
        for (Path source : sources) {
            String code = Files.readString(source)
                    .replaceAll("(?m)^import .*$", "")
                    .replaceAll("(?m)^package .*$", "");
            assertThat(FOREIGN_SCHEMA.matcher(code).find()).as(source.toString()).isFalse();
        }
    }

    @Test
    void producerAuditDeliverySqlReferencesOnlyItsOwnerSchema() throws IOException {
        assertNoForeignSchema(
                Path.of("src/main/java/com/jaxon/inventory/InventoryAuditDeliveryPersistence.java"),
                Pattern.compile(
                        "\\b(discovery|collector|support|ai|identity|automation|audit_reporting)\\.[a-z_]+",
                        Pattern.CASE_INSENSITIVE));
        assertNoForeignSchema(
                Path.of("src/main/java/com/jaxon/collector/CollectorAuditDeliveryPersistence.java"),
                Pattern.compile(
                        "\\b(inventory|discovery|support|ai|identity|automation|audit_reporting)\\.[a-z_]+",
                        Pattern.CASE_INSENSITIVE));
        assertNoForeignSchema(
                Path.of("src/main/java/com/jaxon/discovery/DiscoveryAuditDeliveryPersistence.java"),
                Pattern.compile(
                        "\\b(inventory|collector|support|ai|identity|automation|audit_reporting)\\.[a-z_]+",
                        Pattern.CASE_INSENSITIVE));
        assertNoForeignSchema(
                Path.of("src/main/java/com/jaxon/support/SupportAuditDeliveryPersistence.java"),
                Pattern.compile(
                        "\\b(inventory|discovery|collector|ai|identity|automation|audit_reporting)\\.[a-z_]+",
                        Pattern.CASE_INSENSITIVE));
    }

    private static void assertNoForeignSchema(Path source, Pattern foreignSchema) throws IOException {
        String code = Files.readString(source)
                .replaceAll("(?m)^import .*$", "")
                .replaceAll("(?m)^package .*$", "");
        assertThat(foreignSchema.matcher(code).find()).as(source.toString()).isFalse();
    }
}
