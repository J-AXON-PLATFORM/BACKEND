package com.jaxon.collector;

import java.time.Instant;
import java.util.UUID;

record AgentTaskMessage(
        String contractVersion,
        String messageType,
        UUID messageId,
        UUID agentId,
        Instant createdAt,
        UUID taskId,
        String taskType,
        Instant expiresAt) {}
