package com.jaxon.collector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jaxon.collector.audit-dispatch.enabled", havingValue = "true", matchIfMissing = true)
class CollectorAuditDispatchScheduler {

    private final CollectorAuditDispatcher dispatcher;
    private final int batchSize;

    CollectorAuditDispatchScheduler(
            CollectorAuditDispatcher dispatcher,
            @Value("${jaxon.collector.audit-dispatch.batch-size:50}") int batchSize) {
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalArgumentException("Collector audit dispatch batch size must be between 1 and 200");
        }
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${jaxon.collector.audit-dispatch.poll-interval:1s}",
            initialDelayString = "${jaxon.collector.audit-dispatch.initial-delay:1s}")
    void poll() {
        dispatcher.dispatchDue(batchSize);
    }
}
