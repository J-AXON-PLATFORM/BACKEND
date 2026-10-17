package com.jaxon.collector;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class TechnicalStateEventListener {

    private final TechnicalStateEventBroadcaster broadcaster;

    TechnicalStateEventListener(TechnicalStateEventBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void afterAcceptedSnapshot(TechnicalStateUpdatedEvent event) {
        broadcaster.publish(event);
    }
}
