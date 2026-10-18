package com.jaxon.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AgentOperationalStateServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final Duration THRESHOLD = Duration.ofMinutes(5);

    @Test
    void derivesUnknownWithoutAcceptedContact() {
        assertThat(AgentOperationalStateService.derive(null, NOW, THRESHOLD))
                .isEqualTo(AgentOperationalStatus.UNKNOWN);
    }

    @Test
    void derivesOnlineAtAndWithinTheThreshold() {
        assertThat(AgentOperationalStateService.derive(NOW.minus(THRESHOLD), NOW, THRESHOLD))
                .isEqualTo(AgentOperationalStatus.ONLINE);
        assertThat(AgentOperationalStateService.derive(NOW.minusSeconds(1), NOW, THRESHOLD))
                .isEqualTo(AgentOperationalStatus.ONLINE);
    }

    @Test
    void derivesOfflineOnlyAfterTheThreshold() {
        assertThat(AgentOperationalStateService.derive(
                        NOW.minus(THRESHOLD).minusNanos(1_000), NOW, THRESHOLD))
                .isEqualTo(AgentOperationalStatus.OFFLINE);
    }
}
