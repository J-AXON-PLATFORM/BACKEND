package com.jaxon.support;

import com.jaxon.AuditFact;
import com.jaxon.AuditFactRecorder;
import com.jaxon.AuditFactRejectedException;
import com.jaxon.AuditRecordOutcome;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongUnaryOperator;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Support-owned dispatcher of allowlisted Ticket 2.0 events to Audit/Reporting. */
@Component
class SupportAuditDispatcher {

    static final Duration BASE_DELAY = Duration.ofSeconds(1);
    static final Duration MAX_DELAY = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger(SupportAuditDispatcher.class);
    private static final Set<String> TICKET_TYPES =
            Set.of("INCIDENT", "SERVICE_REQUEST", "QUESTION", "OTHER");
    private static final Set<String> PRIORITIES =
            Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> COMMENT_VISIBILITIES = Set.of("PUBLIC", "INTERNAL");
    private static final Set<String> OPERATIONAL_STATUSES =
            Set.of("OPEN", "IN_PROGRESS", "WAITING_FOR_REQUESTER");
    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private final SupportAuditDeliveryPersistence persistence;
    private final AuditFactRecorder recorder;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final Clock clock;
    private final LongUnaryOperator jitter;

    @Autowired
    SupportAuditDispatcher(
            SupportAuditDeliveryPersistence persistence,
            AuditFactRecorder recorder,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            MeterRegistry meters,
            Clock clock) {
        this(persistence, recorder, transactionManager, objectMapper, meters, clock,
                bound -> ThreadLocalRandom.current().nextLong(bound + 1));
    }

    SupportAuditDispatcher(
            SupportAuditDeliveryPersistence persistence,
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
                    Optional<SupportAuditDeliveryPersistence.DueEvent> due = persistence.claimNextDue(now);
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
                LOG.warn("Support audit dispatch could not claim outbox events: {}", errorCode);
                break;
            }
            UUID eventId = claimed[0];
            String code = errorCode;
            Optional<SupportAuditDeliveryPersistence.FailureState> state = transactions.execute(
                    status -> persistence.recordFailure(eventId, code, now, attempts -> now.plus(delay(attempts))));
            if (state != null && state.isPresent()) {
                if ("FAILED".equals(state.get().status())) {
                    failed++;
                    count("failed");
                } else {
                    retried++;
                    count("retry");
                }
                LOG.warn("Support audit delivery attempt failed eventId={} attempts={} code={}",
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

    private AuditFact fact(SupportAuditDeliveryPersistence.DueEvent event) {
        return new AuditFact(
                event.eventId(),
                event.eventType(),
                event.eventVersion(),
                "SUPPORT",
                event.occurredAt(),
                event.actorType(),
                event.actorId(),
                event.resourceType(),
                event.resourceId(),
                event.resourceVersion(),
                event.result(),
                event.correlationId(),
                metadata(event));
    }

    private Map<String, String> metadata(SupportAuditDeliveryPersistence.DueEvent event) {
        try {
            Map<String, String> metadata =
                    objectMapper.readValue(event.payload(), new TypeReference<Map<String, String>>() {});
            if ("TICKET_CREATED".equals(event.eventType())
                    && metadata.size() == 4
                    && TICKET_TYPES.contains(metadata.get("type"))
                    && "OPEN".equals(metadata.get("status"))
                    && "NORMAL".equals(metadata.get("priority"))
                    && "WEB".equals(metadata.get("source"))) {
                return metadata;
            }
            if ("TICKET_COMMENT_ADDED".equals(event.eventType())
                    && metadata.size() == 2
                    && canonicalUuid(metadata.get("commentId"))
                    && COMMENT_VISIBILITIES.contains(metadata.get("visibility"))) {
                return metadata;
            }
            if ("TICKET_ASSIGNED".equals(event.eventType())
                    && metadata.size() == 1
                    && canonicalUuid(metadata.get("assigneeUserId"))
                    && event.actorId().toString().equals(metadata.get("assigneeUserId"))) {
                return metadata;
            }
            if ("TICKET_PRIORITY_CHANGED".equals(event.eventType())
                    && metadata.size() == 2
                    && PRIORITIES.contains(metadata.get("previousPriority"))
                    && PRIORITIES.contains(metadata.get("newPriority"))
                    && !metadata.get("previousPriority").equals(metadata.get("newPriority"))) {
                return metadata;
            }
            if ("TICKET_STATUS_CHANGED".equals(event.eventType())
                    && metadata.size() == 2
                    && validOperationalTransition(
                            metadata.get("previousStatus"), metadata.get("newStatus"))) {
                return metadata;
            }
            if ("TICKET_RESOLVED".equals(event.eventType())
                    && lifecycleMetadata(metadata, 3)
                    && Set.of("IN_PROGRESS", "WAITING_FOR_REQUESTER")
                            .contains(metadata.get("fromStatus"))
                    && "RESOLVED".equals(metadata.get("toStatus"))) {
                return metadata;
            }
            if ("TICKET_CLOSED".equals(event.eventType())
                    && lifecycleMetadata(metadata, 4)
                    && "CLOSED".equals(metadata.get("toStatus"))
                    && validClosure(metadata.get("fromStatus"), metadata.get("closeReason"))) {
                return metadata;
            }
            if ("TICKET_REOPENED".equals(event.eventType())
                    && lifecycleMetadata(metadata, 3)
                    && Set.of("RESOLVED", "CLOSED").contains(metadata.get("fromStatus"))
                    && "OPEN".equals(metadata.get("toStatus"))) {
                return metadata;
            }
            throw new InvalidAuditPayloadException();
        } catch (InvalidAuditPayloadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidAuditPayloadException();
        }
    }

    private static boolean canonicalUuid(String value) {
        return value != null && UUID_V4.matcher(value).matches();
    }

    private static boolean validOperationalTransition(String previous, String next) {
        if (!OPERATIONAL_STATUSES.contains(previous) || !OPERATIONAL_STATUSES.contains(next)) {
            return false;
        }
        return ("OPEN".equals(previous) && "IN_PROGRESS".equals(next))
                || ("IN_PROGRESS".equals(previous) && "WAITING_FOR_REQUESTER".equals(next))
                || ("WAITING_FOR_REQUESTER".equals(previous) && "IN_PROGRESS".equals(next));
    }

    private static boolean lifecycleMetadata(Map<String, String> metadata, int size) {
        return metadata.size() == size
                && canonicalUuid(metadata.get("lifecycleEventId"))
                && metadata.get("fromStatus") != null
                && metadata.get("toStatus") != null;
    }

    private static boolean validClosure(String fromStatus, String reason) {
        return ("RESOLVED".equals(fromStatus) && "RESOLVED".equals(reason))
                || (Set.of("OPEN", "IN_PROGRESS", "WAITING_FOR_REQUESTER").contains(fromStatus)
                        && Set.of("CANCELLED", "DUPLICATE", "NOT_APPLICABLE").contains(reason));
    }

    private void count(String outcome) {
        Counter.builder("jaxon.support.audit.dispatch")
                .tag("consumer", SupportAuditDeliveryPersistence.CONSUMER)
                .tag("outcome", outcome)
                .register(meters)
                .increment();
    }

    record DispatchSummary(int delivered, int duplicates, int retried, int failed) {}

    private static final class InvalidAuditPayloadException extends RuntimeException {}
}
