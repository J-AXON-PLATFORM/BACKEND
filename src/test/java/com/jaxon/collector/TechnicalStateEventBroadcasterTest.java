package com.jaxon.collector;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TechnicalStateEventBroadcasterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T20:00:00Z"), ZoneOffset.UTC);

    @Test
    void enforcesTheGlobalConnectionLimit() {
        TechnicalStateEventBroadcaster broadcaster = broadcaster(1, 1);
        broadcaster.subscribe(UUID.randomUUID(), UUID.randomUUID(), Instant.now(CLOCK).plusSeconds(60));

        assertThatThrownBy(() -> broadcaster.subscribe(
                        UUID.randomUUID(), UUID.randomUUID(), Instant.now(CLOCK).plusSeconds(60)))
                .isInstanceOf(TechnicalStateEventBroadcaster.ConnectionLimitExceededException.class);
        broadcaster.close();
    }

    @Test
    void enforcesThePerUserConnectionLimit() {
        UUID userId = UUID.randomUUID();
        TechnicalStateEventBroadcaster broadcaster = broadcaster(2, 1);
        broadcaster.subscribe(UUID.randomUUID(), userId, Instant.now(CLOCK).plusSeconds(60));

        assertThatThrownBy(() -> broadcaster.subscribe(
                        UUID.randomUUID(), userId, Instant.now(CLOCK).plusSeconds(60)))
                .isInstanceOf(TechnicalStateEventBroadcaster.ConnectionLimitExceededException.class);
        broadcaster.close();
    }

    private static TechnicalStateEventBroadcaster broadcaster(int globalLimit, int userLimit) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new TechnicalStateEventBroadcaster(
                globalLimit,
                userLimit,
                Duration.ofSeconds(60),
                new TechnicalStateEventMetrics(registry),
                CLOCK,
                Runnable::run);
    }
}
