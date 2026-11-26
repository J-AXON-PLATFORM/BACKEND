package com.jaxon.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AiAuditDispatcherDelayTest {

    @Test
    void usesExponentialBackoffWithBoundedJitterAndAFiveMinuteCap() {
        AiAuditDispatcher minimum = new AiAuditDispatcher(null, null, null, null, null, null, bound -> 0L);
        AiAuditDispatcher maximum = new AiAuditDispatcher(null, null, null, null, null, null, bound -> bound);
        assertThat(minimum.delay(1)).isEqualTo(Duration.ofMillis(500));
        assertThat(maximum.delay(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(maximum.delay(2)).isEqualTo(Duration.ofSeconds(2));
        assertThat(maximum.delay(5)).isEqualTo(Duration.ofSeconds(16));
        assertThat(minimum.delay(9)).isEqualTo(Duration.ofSeconds(128));
        assertThat(maximum.delay(10)).isEqualTo(Duration.ofMinutes(5));
        assertThat(maximum.delay(40)).isEqualTo(Duration.ofMinutes(5));
        assertThat(minimum.delay(40)).isEqualTo(Duration.ofSeconds(150));
    }
}
