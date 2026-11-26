package com.jaxon.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DiscoveryAuditDispatcherDelayTest {

    @Test
    void usesExponentialBackoffWithBoundedJitterAndAFiveMinuteCap() {
        DiscoveryAuditDispatcher minimum =
                new DiscoveryAuditDispatcher(null, null, null, null, null, null, bound -> 0L);
        DiscoveryAuditDispatcher maximum =
                new DiscoveryAuditDispatcher(null, null, null, null, null, null, bound -> bound);

        assertThat(minimum.delay(1)).isEqualTo(Duration.ofMillis(500));
        assertThat(maximum.delay(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(minimum.delay(10)).isEqualTo(Duration.ofMinutes(2).plusSeconds(30));
        assertThat(maximum.delay(10)).isEqualTo(Duration.ofMinutes(5));
        assertThat(maximum.delay(30)).isEqualTo(Duration.ofMinutes(5));
    }
}
