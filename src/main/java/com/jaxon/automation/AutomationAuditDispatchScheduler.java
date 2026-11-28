package com.jaxon.automation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "jaxon.automation.audit-dispatch.enabled",
        havingValue = "true",
        matchIfMissing = true)
class AutomationAuditDispatchScheduler {

    private final AutomationAuditDispatcher dispatcher;
    private final int batchSize;

    AutomationAuditDispatchScheduler(
            AutomationAuditDispatcher dispatcher,
            @Value("${jaxon.automation.audit-dispatch.batch-size:50}") int batchSize) {
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalArgumentException(
                    "Automation audit dispatch batch size must be between 1 and 200");
        }
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${jaxon.automation.audit-dispatch.poll-interval:1s}",
            initialDelayString = "${jaxon.automation.audit-dispatch.initial-delay:1s}")
    void poll() {
        dispatcher.dispatchDue(batchSize);
    }
}
