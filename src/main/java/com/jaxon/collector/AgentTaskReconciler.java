package com.jaxon.collector;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class AgentTaskReconciler {

    private final AgentTaskService tasks;
    private final AgentResultReconciler results;
    private final AtomicBoolean running = new AtomicBoolean();

    AgentTaskReconciler(AgentTaskService tasks, AgentResultReconciler results) {
        this.tasks = tasks;
        this.results = results;
    }

    @Scheduled(
            initialDelayString = "${jaxon.collector.tasks.reconciliation-interval:5m}",
            fixedDelayString = "${jaxon.collector.tasks.reconciliation-interval:5m}")
    void reconcile() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            results.reconcile();
            tasks.reconcilePending();
        } finally {
            running.set(false);
        }
    }
}
