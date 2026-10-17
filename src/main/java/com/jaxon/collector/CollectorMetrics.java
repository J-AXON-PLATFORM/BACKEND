package com.jaxon.collector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class CollectorMetrics {

    private final MeterRegistry registry;
    private final Map<String, Counter> outcomes = new ConcurrentHashMap<>();
    private final Timer processing;

    CollectorMetrics(MeterRegistry registry) {
        this.registry = registry;
        processing = Timer.builder("jaxon.collector.snapshot.processing")
                .description("Snapshot file processing duration")
                .register(registry);
    }

    void outcome(String outcome) {
        outcomes.computeIfAbsent(outcome, value -> Counter.builder("jaxon.collector.snapshot.outcomes")
                        .description("Snapshot ingestion outcomes")
                        .tag("outcome", value)
                        .register(registry))
                .increment();
    }

    Timer.Sample start() {
        return Timer.start(registry);
    }

    void stop(Timer.Sample sample) {
        sample.stop(processing);
    }
}
