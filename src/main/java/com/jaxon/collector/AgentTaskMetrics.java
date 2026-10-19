package com.jaxon.collector;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
class AgentTaskMetrics {

    private final MeterRegistry registry;

    AgentTaskMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void created() {
        registry.counter("jaxon.collector.tasks.created", "type", "collect_snapshot").increment();
    }

    void dispatch(String outcome) {
        registry.counter("jaxon.collector.tasks.dispatch", "outcome", outcome).increment();
    }

    void result(String status) {
        registry.counter("jaxon.collector.tasks.results", "status", status.toLowerCase()).increment();
    }

    void expiration() {
        registry.counter("jaxon.collector.tasks.expirations").increment();
    }

    void reconciliation(String outcome) {
        registry.counter("jaxon.collector.tasks.reconciliation", "outcome", outcome).increment();
    }

    void executionDuration(Duration duration, String status) {
        Timer.builder("jaxon.collector.tasks.execution.duration")
                .tag("status", status.toLowerCase())
                .register(registry)
                .record(duration);
    }
}
