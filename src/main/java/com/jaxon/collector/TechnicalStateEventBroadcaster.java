package com.jaxon.collector;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
class TechnicalStateEventBroadcaster {

    private final Object monitor = new Object();
    private final Map<UUID, Subscription> subscriptions = new HashMap<>();
    private final Map<UUID, Integer> subscriptionsPerUser = new HashMap<>();
    private final int globalLimit;
    private final int userLimit;
    private final Duration timeout;
    private final TechnicalStateEventMetrics metrics;
    private final Clock clock;
    private final TaskExecutor deliveryExecutor;

    TechnicalStateEventBroadcaster(
            @Value("${jaxon.collector.technical-state-events.global-connection-limit:100}") int globalLimit,
            @Value("${jaxon.collector.technical-state-events.user-connection-limit:3}") int userLimit,
            @Value("${jaxon.collector.technical-state-events.timeout:60s}") Duration timeout,
            TechnicalStateEventMetrics metrics,
            Clock clock,
            @Qualifier("sseTaskExecutor") TaskExecutor deliveryExecutor) {
        if (globalLimit < 1 || userLimit < 1 || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Invalid technical-state SSE limits");
        }
        this.globalLimit = globalLimit;
        this.userLimit = userLimit;
        this.timeout = timeout;
        this.metrics = metrics;
        this.clock = clock;
        this.deliveryExecutor = deliveryExecutor;
    }

    SseEmitter subscribe(UUID assetId, UUID userId, Instant sessionExpiresAt) {
        UUID subscriptionId = UUID.randomUUID();
        SseEmitter emitter;
        synchronized (monitor) {
            if (subscriptions.size() >= globalLimit
                    || subscriptionsPerUser.getOrDefault(userId, 0) >= userLimit) {
                metrics.limitRejected();
                throw new ConnectionLimitExceededException();
            }
            long sessionRemaining = Math.max(1, Duration.between(Instant.now(clock), sessionExpiresAt).toMillis());
            emitter = new SseEmitter(Math.min(timeout.toMillis(), sessionRemaining));
            subscriptions.put(subscriptionId, new Subscription(assetId, userId, emitter));
            subscriptionsPerUser.merge(userId, 1, Integer::sum);
            metrics.connected();
        }
        emitter.onCompletion(() -> remove(subscriptionId));
        emitter.onTimeout(() -> remove(subscriptionId));
        emitter.onError(error -> remove(subscriptionId));
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException | IllegalStateException exception) {
            metrics.failure();
            remove(subscriptionId);
            throw new SubscriptionUnavailableException(exception);
        }
        return emitter;
    }

    void publish(TechnicalStateUpdatedEvent event) {
        subscriptionsFor(event.assetId()).forEach(subscription -> execute(() -> send(subscription, event)));
    }

    @Scheduled(fixedDelayString = "${jaxon.collector.technical-state-events.heartbeat-interval:15s}")
    void heartbeat() {
        subscriptionsFor(null).forEach(subscription -> execute(() -> sendHeartbeat(subscription)));
    }

    private void send(RegisteredSubscription subscription, TechnicalStateUpdatedEvent event) {
        try {
            subscription.emitter().send(SseEmitter.event()
                    .id(event.eventId().toString())
                    .name(event.eventType())
                    .data(event));
            metrics.sent();
        } catch (IOException | IllegalStateException exception) {
            failed(subscription, exception);
        }
    }

    private void sendHeartbeat(RegisteredSubscription subscription) {
        try {
            subscription.emitter().send(SseEmitter.event().comment("heartbeat"));
        } catch (IOException | IllegalStateException exception) {
            failed(subscription, exception);
        }
    }

    private void execute(Runnable delivery) {
        try {
            deliveryExecutor.execute(delivery);
        } catch (RuntimeException exception) {
            metrics.failure();
        }
    }

    private void failed(RegisteredSubscription subscription, Exception exception) {
        metrics.failure();
        subscription.emitter().completeWithError(exception);
        remove(subscription.id());
    }

    @PreDestroy
    void close() {
        subscriptionsFor(null).forEach(subscription -> {
            subscription.emitter().complete();
            remove(subscription.id());
        });
    }

    private List<RegisteredSubscription> subscriptionsFor(UUID assetId) {
        synchronized (monitor) {
            return subscriptions.entrySet().stream()
                    .filter(entry -> assetId == null || entry.getValue().assetId().equals(assetId))
                    .map(entry -> new RegisteredSubscription(entry.getKey(), entry.getValue().emitter()))
                    .toList();
        }
    }

    private void remove(UUID subscriptionId) {
        synchronized (monitor) {
            Subscription removed = subscriptions.remove(subscriptionId);
            if (removed == null) {
                return;
            }
            subscriptionsPerUser.computeIfPresent(removed.userId(), (userId, count) -> count == 1 ? null : count - 1);
            metrics.disconnected();
        }
    }

    private record Subscription(UUID assetId, UUID userId, SseEmitter emitter) {}

    private record RegisteredSubscription(UUID id, SseEmitter emitter) {}

    static final class ConnectionLimitExceededException extends RuntimeException {}

    static final class SubscriptionUnavailableException extends RuntimeException {
        SubscriptionUnavailableException(Throwable cause) {
            super(cause);
        }
    }
}
