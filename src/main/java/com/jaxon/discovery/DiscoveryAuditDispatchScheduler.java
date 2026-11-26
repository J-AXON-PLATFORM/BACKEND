package com.jaxon.discovery;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jaxon.discovery.audit-dispatch.enabled", havingValue = "true", matchIfMissing = true)
class DiscoveryAuditDispatchScheduler {

    private final DiscoveryAuditDispatcher dispatcher;
    private final int batchSize;

    DiscoveryAuditDispatchScheduler(
            DiscoveryAuditDispatcher dispatcher,
            @Value("${jaxon.discovery.audit-dispatch.batch-size:50}") int batchSize) {
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalArgumentException("Discovery audit dispatch batch size must be between 1 and 200");
        }
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${jaxon.discovery.audit-dispatch.poll-interval:1s}",
            initialDelayString = "${jaxon.discovery.audit-dispatch.initial-delay:1s}")
    void poll() {
        dispatcher.dispatchDue(batchSize);
    }
}
