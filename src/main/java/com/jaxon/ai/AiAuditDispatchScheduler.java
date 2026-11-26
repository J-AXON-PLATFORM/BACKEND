package com.jaxon.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jaxon.ai.audit-dispatch.enabled", havingValue = "true", matchIfMissing = true)
class AiAuditDispatchScheduler {

    private final AiAuditDispatcher dispatcher;
    private final int batchSize;

    AiAuditDispatchScheduler(
            AiAuditDispatcher dispatcher,
            @Value("${jaxon.ai.audit-dispatch.batch-size:50}") int batchSize) {
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalArgumentException("AI audit dispatch batch size must be between 1 and 200");
        }
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${jaxon.ai.audit-dispatch.poll-interval:1s}",
            initialDelayString = "${jaxon.ai.audit-dispatch.initial-delay:1s}")
    void poll() {
        dispatcher.dispatchDue(batchSize);
    }
}
