package com.jaxon.identity;

import com.jaxon.audit_reporting.AuditFact;
import com.jaxon.audit_reporting.AuditFactRecorder;
import com.jaxon.audit_reporting.AuditFactRejectedException;
import com.jaxon.audit_reporting.AuditRecordOutcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Identity-owned dispatcher of allowlisted Identity 2.0 events to Audit/Reporting. */
@Component
class IdentityAuditDispatcher {

    static final Duration BASE_DELAY = Duration.ofSeconds(1);
    static final Duration MAX_DELAY = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger(IdentityAuditDispatcher.class);

    private final IdentityAuditDeliveryPersistence persistence;
    private final AuditFactRecorder recorder;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final Clock clock;
    private final LongUnaryOperator jitter;

    @Autowired
    IdentityAuditDispatcher(
            IdentityAuditDeliveryPersistence persistence,
            AuditFactRecorder recorder,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            MeterRegistry meters,
            Clock clock) {
        this(persistence, recorder, transactionManager, objectMapper, meters, clock,
                bound -> ThreadLocalRandom.current().nextLong(bound + 1));
    }

    IdentityAuditDispatcher(
            IdentityAuditDeliveryPersistence persistence,
            AuditFactRecorder recorder,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            MeterRegistry meters,
            Clock clock,
            LongUnaryOperator jitter) {
        this.persistence = persistence;
        this.recorder = recorder;
        this.transactions = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
        this.meters = meters;
        this.clock = clock;
        this.jitter = jitter;
    }

    DispatchSummary dispatchDue(int limit) {
        int delivered = 0;
        int duplicates = 0;
        int retried = 0;
        int failed = 0;
        for (int index = 0; index < limit; index++) {
            Instant now = Instant.now(clock);
            UUID[] claimed = new UUID[1];
            String errorCode;
            try {
                Optional<AuditRecordOutcome> outcome = transactions.execute(status -> {
                    Optional<IdentityAuditDeliveryPersistence.DueEvent> due = persistence.claimNextDue(now);
                    if (due.isEmpty()) return Optional.empty();
                    claimed[0] = due.get().eventId();
                    AuditRecordOutcome recorded = recorder.record(fact(due.get()));
                    persistence.markDelivered(due.get().eventId(), now);
                    return Optional.of(recorded);
                });
                if (outcome == null || outcome.isEmpty()) break;
                if (outcome.get() == AuditRecordOutcome.RECORDED) {
                    delivered++;
                    count("delivered");
                } else {
                    duplicates++;
                    count("duplicate");
                }
                continue;
            } catch (AuditFactRejectedException exception) {
                errorCode = exception.reason() == AuditFactRejectedException.Reason.CONFLICT
                        ? "AUDIT_CONFLICT"
                        : "AUDIT_REJECTED";
            } catch (InvalidAuditPayloadException exception) {
                errorCode = "AUDIT_REJECTED";
            } catch (RuntimeException exception) {
                errorCode = "AUDIT_UNAVAILABLE";
            }
            if (claimed[0] == null) {
                LOG.warn("Identity audit dispatch could not claim outbox events: {}", errorCode);
                break;
            }
            UUID eventId = claimed[0];
            String code = errorCode;
            Optional<IdentityAuditDeliveryPersistence.FailureState> state = transactions.execute(
                    status -> persistence.recordFailure(eventId, code, now, attempts -> now.plus(delay(attempts))));
            if (state != null && state.isPresent()) {
                if ("FAILED".equals(state.get().status())) {
                    failed++;
                    count("failed");
                } else {
                    retried++;
                    count("retry");
                }
                LOG.warn("Identity audit delivery attempt failed eventId={} attempts={} code={}",
                        eventId, state.get().attempts(), code);
            }
        }
        return new DispatchSummary(delivered, duplicates, retried, failed);
    }

    Duration delay(int attempts) {
        long exponential = BASE_DELAY.toMillis() << Math.min(Math.max(attempts - 1, 0), 20);
        long capped = Math.min(exponential, MAX_DELAY.toMillis());
        long half = capped / 2;
        return Duration.ofMillis(half + jitter.applyAsLong(capped - half));
    }

    private AuditFact fact(IdentityAuditDeliveryPersistence.DueEvent event) {
        Map<String, String> metadata = metadata(event);
        return new AuditFact(
                event.eventId(),
                event.eventType(),
                event.eventVersion(),
                "IDENTITY",
                event.occurredAt(),
                event.actorType(),
                event.actorId(),
                event.resourceType(),
                event.resourceId(),
                null,
                event.result(),
                event.correlationId(),
                metadata);
    }

    private Map<String, String> metadata(IdentityAuditDeliveryPersistence.DueEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.payload());
            if (!payload.isObject()) {
                throw new InvalidAuditPayloadException();
            }
            if ("HUMAN_USER_CREATED".equals(event.eventType())
                    && payload.size() == 2
                    && payload.has("status")
                    && payload.get("status").isTextual()
                    && payload.has("roles")
                    && payload.get("roles").isTextual()) {
                return Map.of(
                    "status", payload.get("status").asText(),
                    "roles", payload.get("roles").asText());
            }
            if (("HUMAN_LOGIN_SUCCEEDED".equals(event.eventType())
                            || "HUMAN_LOGOUT_SUCCEEDED".equals(event.eventType())
                            || "AGENT_PUBLIC_KEY_REGISTERED".equals(event.eventType())
                            || "DISCOVERY_EXECUTOR_PUBLIC_KEY_REGISTERED".equals(event.eventType()))
                    && payload.isEmpty()) {
                return Map.of();
            }
            if ("HUMAN_LOGIN_FAILED".equals(event.eventType())
                    && payload.size() == 1
                    && payload.has("reason")
                    && payload.get("reason").isTextual()
                    && "INVALID_CREDENTIALS".equals(payload.get("reason").asText())) {
                return Map.of("reason", "INVALID_CREDENTIALS");
            }
            throw new InvalidAuditPayloadException();
        } catch (InvalidAuditPayloadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidAuditPayloadException();
        }
    }

    private void count(String outcome) {
        Counter.builder("jaxon.identity.audit.dispatch")
                .tag("consumer", IdentityAuditDeliveryPersistence.CONSUMER)
                .tag("outcome", outcome)
                .register(meters)
                .increment();
    }

    record DispatchSummary(int delivered, int duplicates, int retried, int failed) {}

    private static final class InvalidAuditPayloadException extends RuntimeException {}
}
