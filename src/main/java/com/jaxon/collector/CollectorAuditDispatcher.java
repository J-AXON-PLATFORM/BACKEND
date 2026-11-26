package com.jaxon.collector;

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

/** Collector-owned dispatcher of allowlisted Collector 2.0 events to Audit/Reporting. */
@Component
class CollectorAuditDispatcher {

    static final Duration BASE_DELAY = Duration.ofSeconds(1);
    static final Duration MAX_DELAY = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger(CollectorAuditDispatcher.class);

    private final CollectorAuditDeliveryPersistence persistence;
    private final AuditFactRecorder recorder;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final Clock clock;
    private final LongUnaryOperator jitter;

    @Autowired
    CollectorAuditDispatcher(
            CollectorAuditDeliveryPersistence persistence,
            AuditFactRecorder recorder,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            MeterRegistry meters,
            Clock clock) {
        this(persistence, recorder, transactionManager, objectMapper, meters, clock,
                bound -> ThreadLocalRandom.current().nextLong(bound + 1));
    }

    CollectorAuditDispatcher(
            CollectorAuditDeliveryPersistence persistence,
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
                    Optional<CollectorAuditDeliveryPersistence.DueEvent> due = persistence.claimNextDue(now);
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
                LOG.warn("Collector audit dispatch could not claim outbox events: {}", errorCode);
                break;
            }
            UUID eventId = claimed[0];
            String code = errorCode;
            Optional<CollectorAuditDeliveryPersistence.FailureState> state = transactions.execute(
                    status -> persistence.recordFailure(eventId, code, now, attempts -> now.plus(delay(attempts))));
            if (state != null && state.isPresent()) {
                if ("FAILED".equals(state.get().status())) {
                    failed++;
                    count("failed");
                } else {
                    retried++;
                    count("retry");
                }
                LOG.warn("Collector audit delivery attempt failed eventId={} attempts={} code={}",
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

    private AuditFact fact(CollectorAuditDeliveryPersistence.DueEvent event) {
        Map<String, String> metadata = metadata(event);
        return new AuditFact(
                event.eventId(),
                event.eventType(),
                event.eventVersion(),
                "COLLECTOR",
                event.occurredAt(),
                event.actorType(),
                event.actorId(),
                event.resourceType(),
                event.resourceId(),
                event.resourceVersion(),
                event.result(),
                event.correlationId(),
                metadata);
    }

    private Map<String, String> metadata(CollectorAuditDeliveryPersistence.DueEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.payload());
            if (payload.isObject()
                    && payload.isEmpty()
                    && ("AGENT_PRE_REGISTERED".equals(event.eventType())
                            || "AGENT_ACTIVATED".equals(event.eventType()))) {
                return Map.of();
            }
            throw new InvalidAuditPayloadException();
        } catch (InvalidAuditPayloadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidAuditPayloadException();
        }
    }

    private void count(String outcome) {
        Counter.builder("jaxon.collector.audit.dispatch")
                .tag("consumer", CollectorAuditDeliveryPersistence.CONSUMER)
                .tag("outcome", outcome)
                .register(meters)
                .increment();
    }

    record DispatchSummary(int delivered, int duplicates, int retried, int failed) {}

    private static final class InvalidAuditPayloadException extends RuntimeException {}
}
