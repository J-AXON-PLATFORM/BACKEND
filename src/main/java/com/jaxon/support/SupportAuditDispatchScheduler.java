package com.jaxon.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jaxon.support.audit-dispatch.enabled", havingValue = "true", matchIfMissing = true)
class SupportAuditDispatchScheduler {

    private final SupportAuditDispatcher dispatcher;
    private final int batchSize;

    SupportAuditDispatchScheduler(
            SupportAuditDispatcher dispatcher,
            @Value("${jaxon.support.audit-dispatch.batch-size:50}") int batchSize) {
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalArgumentException("Support audit dispatch batch size must be between 1 and 200");
        }
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${jaxon.support.audit-dispatch.poll-interval:1s}",
            initialDelayString = "${jaxon.support.audit-dispatch.initial-delay:1s}")
    void poll() {
        dispatcher.dispatchDue(batchSize);
    }
}
