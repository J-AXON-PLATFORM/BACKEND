package com.jaxon.ai;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
final class AiIndexingMetrics {

    private final MeterRegistry registry;

    AiIndexingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void completed(String outcome, Duration duration) {
        jobs(outcome).increment();
        Timer.builder("jaxon.ai.indexing.duration")
                .tag("outcome", outcome)
                .register(registry)
                .record(duration);
    }

    void recovered(int count) {
        if (count > 0) {
            jobs("failed").increment(count);
            Counter.builder("jaxon.ai.indexing.recovered")
                    .register(registry)
                    .increment(count);
        }
    }

    private Counter jobs(String outcome) {
        return Counter.builder("jaxon.ai.indexing.jobs")
                .tag("outcome", outcome)
                .register(registry);
    }
}
