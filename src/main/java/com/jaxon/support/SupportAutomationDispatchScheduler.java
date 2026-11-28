package com.jaxon.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "jaxon.support.automation-dispatch.enabled",
        havingValue = "true",
        matchIfMissing = true)
class SupportAutomationDispatchScheduler {

    private final SupportAutomationDispatcher dispatcher;
    private final int batchSize;

    SupportAutomationDispatchScheduler(
            SupportAutomationDispatcher dispatcher,
            @Value("${jaxon.support.automation-dispatch.batch-size:50}") int batchSize) {
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalArgumentException(
                    "Support Automation dispatch batch size must be between 1 and 200");
        }
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${jaxon.support.automation-dispatch.poll-interval:1s}",
            initialDelayString = "${jaxon.support.automation-dispatch.initial-delay:1s}")
    void poll() {
        dispatcher.dispatchDue(batchSize);
    }
}
