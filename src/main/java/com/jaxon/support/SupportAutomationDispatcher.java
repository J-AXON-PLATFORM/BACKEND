package com.jaxon.support;

import com.jaxon.AutomationTrigger;
import com.jaxon.AutomationTriggerOutcome;
import com.jaxon.AutomationTriggerReceiver;
import com.jaxon.AutomationTriggerRejectedException;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
class SupportAutomationDispatcher {

    static final Duration BASE_DELAY = Duration.ofSeconds(1);
    static final Duration MAX_DELAY = Duration.ofMinutes(5);

    private static final Logger LOG = LoggerFactory.getLogger(SupportAutomationDispatcher.class);

    private final SupportAutomationDeliveryPersistence persistence;
    private final AutomationTriggerReceiver receiver;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;
    private final Clock clock;
    private final LongUnaryOperator jitter;

    @Autowired
    SupportAutomationDispatcher(
            SupportAutomationDeliveryPersistence persistence,
            AutomationTriggerReceiver receiver,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            MeterRegistry meters,
            Clock clock) {
        this(persistence, receiver, transactionManager, objectMapper, meters, clock,
                bound -> ThreadLocalRandom.current().nextLong(bound + 1));
    }

    SupportAutomationDispatcher(
            SupportAutomationDeliveryPersistence persistence,
            AutomationTriggerReceiver receiver,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            MeterRegistry meters,
            Clock clock,
            LongUnaryOperator jitter) {
        this.persistence = persistence;
        this.receiver = receiver;
        this.transactions = new TransactionTemplate(transactionManager);
        this.objectMapper = objectMapper;
        this.meters = meters;
        this.clock = clock;
        this.jitter = jitter;
    }

    DispatchSummary dispatchDue(int limit) {
        int accepted = 0;
        int duplicates = 0;
        int retried = 0;
        int failed = 0;
        for (int index = 0; index < limit; index++) {
            Instant now = Instant.now(clock);
            UUID[] claimed = new UUID[1];
            String errorCode;
            try {
                Optional<AutomationTriggerOutcome> outcome = transactions.execute(status -> {
                    Optional<SupportAutomationDeliveryPersistence.DueEvent> due =
                            persistence.claimNextDue(now);
                    if (due.isEmpty()) return Optional.empty();
                    claimed[0] = due.orElseThrow().eventId();
                    AutomationTriggerOutcome received = receiver.accept(trigger(due.orElseThrow()));
                    persistence.markDelivered(due.orElseThrow().eventId(), now);
                    return Optional.of(received);
                });
                if (outcome == null || outcome.isEmpty()) break;
                if (outcome.orElseThrow() == AutomationTriggerOutcome.ACCEPTED) {
                    accepted++;
                    count("accepted");
                } else {
                    duplicates++;
                    count("duplicate");
                }
                continue;
            } catch (AutomationTriggerRejectedException exception) {
                errorCode = exception.reason() == AutomationTriggerRejectedException.Reason.CONFLICT
                        ? "AUTOMATION_CONFLICT"
                        : "AUTOMATION_REJECTED";
            } catch (InvalidAutomationPayloadException exception) {
                errorCode = "AUTOMATION_REJECTED";
            } catch (RuntimeException exception) {
                errorCode = "AUTOMATION_UNAVAILABLE";
            }
            if (claimed[0] == null) {
                LOG.warn("Support Automation dispatch could not claim outbox events: {}", errorCode);
                break;
            }
            UUID eventId = claimed[0];
            String code = errorCode;
            Optional<SupportAutomationDeliveryPersistence.FailureState> state = transactions.execute(
                    status -> persistence.recordFailure(
                            eventId, code, now, attempts -> now.plus(delay(attempts))));
            if (state != null && state.isPresent()) {
                if ("FAILED".equals(state.orElseThrow().status())) {
                    failed++;
                    count("failed");
                } else {
                    retried++;
                    count("retry");
                }
                LOG.warn("Support Automation delivery attempt failed eventId={} attempts={} code={}",
                        eventId, state.orElseThrow().attempts(), code);
            }
        }
        return new DispatchSummary(accepted, duplicates, retried, failed);
    }

    Duration delay(int attempts) {
        long exponential = BASE_DELAY.toMillis() << Math.min(Math.max(attempts - 1, 0), 20);
        long capped = Math.min(exponential, MAX_DELAY.toMillis());
        long half = capped / 2;
        return Duration.ofMillis(half + jitter.applyAsLong(capped - half));
    }

    private AutomationTrigger trigger(SupportAutomationDeliveryPersistence.DueEvent event) {
        Map<String, String> payload;
        try {
            payload = objectMapper.readValue(
                    event.payload(), new TypeReference<Map<String, String>>() {});
        } catch (RuntimeException exception) {
            throw new InvalidAutomationPayloadException();
        }
        if (!payload.equals(Map.of(
                "previousStatus", "IN_PROGRESS",
                "newStatus", "WAITING_FOR_REQUESTER"))) {
            throw new InvalidAutomationPayloadException();
        }
        return new AutomationTrigger(
                event.eventId(),
                event.eventType(),
                event.eventVersion(),
                event.occurredAt(),
                event.producerModule(),
                event.resourceType(),
                event.resourceId(),
                event.resourceVersion(),
                event.actorType(),
                event.actorId(),
                event.correlationId(),
                event.requesterUserId(),
                payload.get("previousStatus"),
                payload.get("newStatus"));
    }

    private void count(String outcome) {
        Counter.builder("jaxon.support.automation.dispatch")
                .tag("consumer", SupportAutomationDeliveryPersistence.CONSUMER)
                .tag("outcome", outcome)
                .register(meters)
                .increment();
    }

    record DispatchSummary(int accepted, int duplicates, int retried, int failed) {}

    private static final class InvalidAutomationPayloadException extends RuntimeException {}
}
