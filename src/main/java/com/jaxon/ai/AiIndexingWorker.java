package com.jaxon.ai;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "jaxon.ai.indexing.enabled",
        havingValue = "true",
        matchIfMissing = true)
final class AiIndexingWorker {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiIndexingWorker.class);
    private static final int MAX_CONCURRENCY = 4;

    private final AiIndexingPersistence persistence;
    private final AiIndexingService service;
    private final AiIndexingMetrics metrics;
    private final Clock clock;
    private final Duration claimTimeout;
    private final int batchSize;
    private final int concurrency;
    private final AtomicInteger active = new AtomicInteger();
    private final ExecutorService executor;

    AiIndexingWorker(
            AiIndexingPersistence persistence,
            AiIndexingService service,
            AiIndexingMetrics metrics,
            Clock clock,
            @Value("${jaxon.ai.indexing.claim-timeout:10m}") Duration claimTimeout,
            @Value("${jaxon.ai.indexing.batch-size:16}") int batchSize,
            @Value("${jaxon.ai.indexing.concurrency:1}") int concurrency) {
        if (claimTimeout.isNegative() || claimTimeout.isZero()) {
            throw new IllegalArgumentException("AI indexing claim timeout must be positive");
        }
        if (batchSize < 1 || batchSize > 64) {
            throw new IllegalArgumentException("AI indexing batch size must be between 1 and 64");
        }
        if (concurrency < 1 || concurrency > MAX_CONCURRENCY) {
            throw new IllegalArgumentException("AI indexing concurrency must be between 1 and 4");
        }
        this.persistence = persistence;
        this.service = service;
        this.metrics = metrics;
        this.clock = clock;
        this.claimTimeout = claimTimeout;
        this.batchSize = batchSize;
        this.concurrency = concurrency;
        this.executor = Executors.newFixedThreadPool(concurrency, Thread.ofPlatform()
                .name("ai-indexer-", 0)
                .factory());
    }

    @Scheduled(
            fixedDelayString = "${jaxon.ai.indexing.poll-interval:1s}",
            initialDelayString = "${jaxon.ai.indexing.initial-delay:0s}")
    void poll() {
        int recovered = persistence.recoverExpired(Instant.now(clock));
        metrics.recovered(recovered);
        while (active.get() < concurrency) {
            var claimed = persistence.claimNext(Instant.now(clock), claimTimeout);
            if (claimed.isEmpty()) return;
            active.incrementAndGet();
            executor.execute(() -> process(claimed.orElseThrow()));
        }
    }

    private void process(AiIndexingPersistence.ClaimedJob job) {
        Instant started = Instant.now(clock);
        MDC.put("correlationId", job.correlationId().toString());
        String outcome = "discarded";
        try {
            AiIndexingService.ProcessOutcome result = service.process(job, batchSize);
            outcome = result.name().toLowerCase(java.util.Locale.ROOT);
            if (result == AiIndexingService.ProcessOutcome.FAILED) {
                LOGGER.warn("AI source indexing failed");
            } else if (result == AiIndexingService.ProcessOutcome.DISCARDED) {
                LOGGER.info("Discarded late AI source indexing result");
            }
        } catch (RuntimeException exception) {
            outcome = "failed";
            LOGGER.warn("AI source indexing worker failed unexpectedly");
        } finally {
            metrics.completed(outcome, Duration.between(started, Instant.now(clock)));
            MDC.remove("correlationId");
            active.decrementAndGet();
        }
    }

    @PreDestroy
    void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(20, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    LOGGER.warn("AI indexing workers did not stop within the shutdown window");
                }
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
