package com.jaxon.collector;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
class AgentStatusMetrics {

    AgentStatusMetrics(MeterRegistry registry, AgentOperationalStateService states) {
        for (String status : List.of("PENDING", "ACTIVE")) {
            Gauge.builder(
                            "jaxon.collector.agents.administrative",
                            states,
                            service -> service.countAdministrative(status))
                    .description("Agents by administrative status")
                    .tag("status", status.toLowerCase())
                    .register(registry);
        }
        for (AgentOperationalStatus status : AgentOperationalStatus.values()) {
            Gauge.builder(
                            "jaxon.collector.agents.operational",
                            states,
                            service -> service.countOperational(status))
                    .description("Agents by derived operational status")
                    .tag("status", status.name().toLowerCase())
                    .register(registry);
        }
    }
}
