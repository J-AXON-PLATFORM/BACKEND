package com.jaxon.collector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CollectorAuditDispatcherDelayTest {

    @Test
    void usesExponentialBackoffWithBoundedJitterAndAFiveMinuteCap() {
        CollectorAuditDispatcher minimum =
                new CollectorAuditDispatcher(null, null, null, null, null, null, bound -> 0L);
        CollectorAuditDispatcher maximum =
                new CollectorAuditDispatcher(null, null, null, null, null, null, bound -> bound);

        assertThat(minimum.delay(1)).isEqualTo(Duration.ofMillis(500));
        assertThat(maximum.delay(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(minimum.delay(10)).isEqualTo(Duration.ofMinutes(2).plusSeconds(30));
        assertThat(maximum.delay(10)).isEqualTo(Duration.ofMinutes(5));
        assertThat(maximum.delay(30)).isEqualTo(Duration.ofMinutes(5));
    }
}
