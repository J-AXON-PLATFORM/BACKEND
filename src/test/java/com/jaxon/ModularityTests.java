package com.jaxon;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    private static final Set<String> EXPECTED_MODULES = Set.of(
            "inventory",
            "discovery",
            "collector",
            "support",
            "ai",
            "identity",
            "automation",
            "audit_reporting");

    private final ApplicationModules modules = ApplicationModules.of(JaxonApplication.class);

    @Test
    void verifiesExactlyTheApprovedModules() {
        modules.verify();

        assertThat(modules.stream()).hasSize(EXPECTED_MODULES.size());
        assertThat(EXPECTED_MODULES)
                .allSatisfy(moduleName -> assertThat(modules.getModuleByName(moduleName)).isPresent());
    }
}
