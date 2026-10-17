package com.jaxon.collector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
class TechnicalStateEventMetrics {

    private final AtomicInteger activeConnections = new AtomicInteger();
    private final Counter sent;
    private final Counter failures;
    private final Counter limitRejections;

    TechnicalStateEventMetrics(MeterRegistry registry) {
        Gauge.builder("jaxon.collector.sse.connections.active", activeConnections, AtomicInteger::get)
                .description("Active technical-state SSE connections")
                .register(registry);
        sent = Counter.builder("jaxon.collector.sse.events.sent")
                .description("Technical-state SSE events sent")
                .register(registry);
        failures = Counter.builder("jaxon.collector.sse.failures")
                .description("Technical-state SSE send failures")
                .register(registry);
        limitRejections = Counter.builder("jaxon.collector.sse.limit.rejections")
                .description("Technical-state SSE connections rejected by limits")
                .register(registry);
    }

    void connected() {
        activeConnections.incrementAndGet();
    }

    void disconnected() {
        activeConnections.decrementAndGet();
    }

    void sent() {
        sent.increment();
    }

    void failure() {
        failures.increment();
    }

    void limitRejected() {
        limitRejections.increment();
    }
}
