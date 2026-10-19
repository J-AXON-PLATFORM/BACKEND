package com.jaxon.collector;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;

record AgentTaskCreateRequest(@NotNull TaskType taskType) {

    @JsonAnySetter
    void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Unknown task request property");
    }

    enum TaskType {
        COLLECT_SNAPSHOT
    }
}
