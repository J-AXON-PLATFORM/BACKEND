package com.jaxon.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jaxon.identity.audit-dispatch.enabled", havingValue = "true", matchIfMissing = true)
class IdentityAuditDispatchScheduler {

    private final IdentityAuditDispatcher dispatcher;
    private final int batchSize;

    IdentityAuditDispatchScheduler(
            IdentityAuditDispatcher dispatcher,
            @Value("${jaxon.identity.audit-dispatch.batch-size:50}") int batchSize) {
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalArgumentException("Identity audit dispatch batch size must be between 1 and 200");
        }
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${jaxon.identity.audit-dispatch.poll-interval:1s}",
            initialDelayString = "${jaxon.identity.audit-dispatch.initial-delay:1s}")
    void poll() {
        dispatcher.dispatchDue(batchSize);
    }
}
