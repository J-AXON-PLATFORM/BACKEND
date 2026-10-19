package com.jaxon.collector;

import java.time.Instant;
import java.util.UUID;

record AgentTaskResponse(
        UUID taskId,
        UUID agentId,
        String taskType,
        AgentTaskStatus status,
        Instant createdAt,
        Instant expiresAt,
        Instant dispatchedAt,
        Instant completedAt,
        TaskError error) {

    record TaskError(String code, String message) {}
}
