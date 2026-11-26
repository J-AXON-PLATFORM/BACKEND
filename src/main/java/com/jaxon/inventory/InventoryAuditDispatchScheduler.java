package com.jaxon.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "jaxon.inventory.audit-dispatch.enabled", havingValue = "true", matchIfMissing = true)
class InventoryAuditDispatchScheduler {

    private final InventoryAuditDispatcher dispatcher;
    private final int batchSize;

    InventoryAuditDispatchScheduler(
            InventoryAuditDispatcher dispatcher,
            @Value("${jaxon.inventory.audit-dispatch.batch-size:50}") int batchSize) {
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalArgumentException("Inventory audit dispatch batch size must be between 1 and 200");
        }
        this.dispatcher = dispatcher;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${jaxon.inventory.audit-dispatch.poll-interval:1s}",
            initialDelayString = "${jaxon.inventory.audit-dispatch.initial-delay:1s}")
    void poll() {
        dispatcher.dispatchDue(batchSize);
    }
}
