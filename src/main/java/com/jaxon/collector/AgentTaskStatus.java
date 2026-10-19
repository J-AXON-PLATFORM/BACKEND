package com.jaxon.collector;

enum AgentTaskStatus {
    PENDING,
    DISPATCHED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    REJECTED,
    EXPIRED;

    boolean terminal() {
        return this == SUCCEEDED || this == FAILED || this == REJECTED || this == EXPIRED;
    }
}
