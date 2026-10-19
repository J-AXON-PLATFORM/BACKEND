package com.jaxon.collector;

import java.time.Instant;
import java.util.UUID;

record AgentResultMessage(
        String contractVersion,
        UUID messageId,
        UUID agentId,
        Instant createdAt,
        UUID resultId,
        UUID taskId,
        String taskType,
        String status,
        Instant completedAt,
        ResultError error) {

    record ResultError(String code, String message) {}
}
