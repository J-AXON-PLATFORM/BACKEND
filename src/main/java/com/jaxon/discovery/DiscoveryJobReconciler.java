package com.jaxon.discovery;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class DiscoveryJobReconciler {
    private final DiscoveryJobService jobs;
    private final DiscoveryResultReconciler results;
    private final AtomicBoolean running = new AtomicBoolean();

    DiscoveryJobReconciler(DiscoveryJobService jobs, DiscoveryResultReconciler results) {
        this.jobs = jobs;
        this.results = results;
    }

    @Scheduled(
            initialDelayString = "${jaxon.discovery.jobs.reconciliation-interval:5m}",
            fixedDelayString = "${jaxon.discovery.jobs.reconciliation-interval:5m}")
    void reconcile() {
        if (!running.compareAndSet(false, true)) return;
        try {
            results.reconcile();
            jobs.reconcilePending();
        } finally {
            running.set(false);
        }
    }
}
