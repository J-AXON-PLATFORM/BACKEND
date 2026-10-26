package com.jaxon.discovery;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class DiscoveryJobReconciler {
    private final DiscoveryJobService jobs;
    private final AtomicBoolean running = new AtomicBoolean();

    DiscoveryJobReconciler(DiscoveryJobService jobs) {
        this.jobs = jobs;
    }

    @Scheduled(
            initialDelayString = "${jaxon.discovery.jobs.reconciliation-interval:5m}",
            fixedDelayString = "${jaxon.discovery.jobs.reconciliation-interval:5m}")
    void reconcile() {
        if (!running.compareAndSet(false, true)) return;
        try {
            jobs.reconcilePending();
        } finally {
            running.set(false);
        }
    }
}
