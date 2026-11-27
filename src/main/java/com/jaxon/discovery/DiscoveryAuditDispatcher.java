package com.jaxon.discovery;

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

/** Discovery-owned dispatcher of allowlisted Discovery 2.0 events to Audit/Reporting. */
@Component
class DiscoveryAuditDispatcher {

    static final Duration BASE_DELAY = Duration.ofSeconds(1);
    static final Duration MAX_DELAY = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryAuditDispatcher.class);
    private static final Set<String> EMPTY_METADATA_EVENTS = Set.of(
            "DISCOVERY_SCOPE_CREATED",
            "DISCOVERY_EXECUTOR_PRE_REGISTERED",
            "DISCOVERY_EXECUTOR_ACTIVATED",
            "DISCOVERY_JOB_CREATED",
            "DISCOVERY_JOB_DISPATCHED",
            "DISCOVERY_JOB_COMPLETED");
    private static final Set<String> SCOPE_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Pattern UUID_V4 = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private final DiscoveryAuditDeliveryPersistence persistence;
    private final AuditFactRecorder recorder;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final Clock clock;
    private final LongUnaryOperator jitter;

    @Autowired
    DiscoveryAuditDispatcher(
            DiscoveryAuditDeliveryPersistence persistence,
            AuditFactRecorder recorder,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            MeterRegistry meters,
            Clock clock) {
        this(persistence, recorder, transactionManager, objectMapper, meters, clock,
                bound -> ThreadLocalRandom.current().nextLong(bound + 1));
    }

    DiscoveryAuditDispatcher(
            DiscoveryAuditDeliveryPersistence persistence,
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
                    Optional<DiscoveryAuditDeliveryPersistence.DueEvent> due = persistence.claimNextDue(now);
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
                LOG.warn("Discovery audit dispatch could not claim outbox events: {}", errorCode);
                break;
            }
            UUID eventId = claimed[0];
            String code = errorCode;
            Optional<DiscoveryAuditDeliveryPersistence.FailureState> state = transactions.execute(
                    status -> persistence.recordFailure(eventId, code, now, attempts -> now.plus(delay(attempts))));
            if (state != null && state.isPresent()) {
                if ("FAILED".equals(state.get().status())) {
                    failed++;
                    count("failed");
                } else {
                    retried++;
                    count("retry");
                }
                LOG.warn("Discovery audit delivery attempt failed eventId={} attempts={} code={}",
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

    private AuditFact fact(DiscoveryAuditDeliveryPersistence.DueEvent event) {
        return new AuditFact(
                event.eventId(),
                event.eventType(),
                event.eventVersion(),
                "DISCOVERY",
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

    private Map<String, String> metadata(DiscoveryAuditDeliveryPersistence.DueEvent event) {
        try {
            Map<String, String> metadata =
                    objectMapper.readValue(event.payload(), new TypeReference<Map<String, String>>() {});
            if (EMPTY_METADATA_EVENTS.contains(event.eventType()) && metadata.isEmpty()) {
                return metadata;
            }
            if ("DISCOVERY_SCOPE_STATUS_CHANGED".equals(event.eventType())
                    && metadata.size() == 2
                    && SCOPE_STATUSES.contains(metadata.get("previousStatus"))
                    && SCOPE_STATUSES.contains(metadata.get("newStatus"))
                    && !metadata.get("previousStatus").equals(metadata.get("newStatus"))) {
                return metadata;
            }
            if ("DISCOVERED_DEVICE_IGNORED".equals(event.eventType())
                    && metadata.equals(Map.of(
                            "previousStatus", "UNREVIEWED",
                            "newStatus", "IGNORED"))) {
                return metadata;
            }
            if ("DISCOVERED_DEVICE_INCORPORATED".equals(event.eventType())
                    && metadata.size() == 3
                    && "UNREVIEWED".equals(metadata.get("previousStatus"))
                    && "INCORPORATED".equals(metadata.get("newStatus"))
                    && metadata.get("assetId") != null
                    && UUID_V4.matcher(metadata.get("assetId")).matches()) {
                return metadata;
            }
            throw new InvalidAuditPayloadException();
        } catch (InvalidAuditPayloadException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidAuditPayloadException();
        }
    }

    private void count(String outcome) {
        Counter.builder("jaxon.discovery.audit.dispatch")
                .tag("consumer", DiscoveryAuditDeliveryPersistence.CONSUMER)
                .tag("outcome", outcome)
                .register(meters)
                .increment();
    }

    record DispatchSummary(int delivered, int duplicates, int retried, int failed) {}

    private static final class InvalidAuditPayloadException extends RuntimeException {}
}
