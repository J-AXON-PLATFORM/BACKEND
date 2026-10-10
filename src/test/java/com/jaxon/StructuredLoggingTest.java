package com.jaxon;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:1/jaxon_structured_logging_test",
    "spring.datasource.username=jaxon_test",
    "spring.datasource.password=never-log-this-password",
    "spring.datasource.hikari.initialization-fail-timeout=-1",
    "spring.flyway.enabled=false",
    "management.tracing.sampling.probability=1.0",
    "management.tracing.export.otlp.enabled=false"
})
class StructuredLoggingTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(StructuredLoggingTest.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    Tracer tracer;

    @Test
    void writesStructuredSafeLogWithCorrelationAndTraceFields(CapturedOutput output) throws Exception {
        String marker = "structured-observability-test-" + UUID.randomUUID();
        String errorMarker = "structured-observability-error-test-" + UUID.randomUUID();
        String correlationId = UUID.randomUUID().toString();
        Span span = tracer.nextSpan().name("structured-log-test").start();

        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            LOGGER.atInfo()
                    .addKeyValue("operation", "OBSERVABILITY_TEST")
                    .addKeyValue("result", "SUCCESS")
                    .addKeyValue("durationMs", 7)
                    .log(marker);
            LOGGER.atWarn()
                    .addKeyValue("operation", "OBSERVABILITY_TEST")
                    .addKeyValue("result", "FAILED")
                    .addKeyValue("durationMs", 9)
                    .addKeyValue("errorCode", "OBSERVABILITY_TEST_FAILURE")
                    .log(errorMarker);
        } finally {
            span.end();
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }

        String line = output.getOut().lines()
                .filter(candidate -> candidate.contains(marker))
                .findFirst()
                .orElseThrow();
        JsonNode log = JSON.readTree(line);

        assertThat(log.path("@timestamp").asText()).endsWith("Z");
        assertThat(log.path("level").asText()).isEqualTo("INFO");
        assertThat(log.path("logger_name").asText()).contains("StructuredLoggingTest");
        assertThat(log.path("thread_name").asText()).isNotBlank();
        assertThat(log.path("service.name").asText()).isEqualTo("jaxon-backend");
        assertThat(log.path("service.environment").asText()).isEqualTo("local");
        assertThat(log.path("message").asText()).isEqualTo(marker);
        assertThat(log.path("correlationId").asText()).isEqualTo(correlationId);
        assertThat(log.path("traceId").asText()).isEqualTo(span.context().traceId());
        assertThat(log.path("spanId").asText()).isEqualTo(span.context().spanId());
        assertThat(log.path("operation").asText()).isEqualTo("OBSERVABILITY_TEST");
        assertThat(log.path("result").asText()).isEqualTo("SUCCESS");
        assertThat(log.path("durationMs").asInt()).isEqualTo(7);
        String errorLine = output.getOut().lines()
                .filter(candidate -> candidate.contains(errorMarker))
                .findFirst()
                .orElseThrow();
        JsonNode errorLog = JSON.readTree(errorLine);
        assertThat(errorLog.path("result").asText()).isEqualTo("FAILED");
        assertThat(errorLog.path("errorCode").asText()).isEqualTo("OBSERVABILITY_TEST_FAILURE");
        assertThat(output.getOut())
                .doesNotContain("never-log-this-password", "Authorization", "Cookie", "requestBody");
    }
}
