package com.jaxon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesAndReturnsUuidV4WhenHeaderIsMissing() throws Exception {
        String correlationId = invoke(new MockHttpServletRequest());

        assertUuidV4(correlationId);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void preservesValidUuidV4AndMakesItAvailableDuringRequest() throws Exception {
        String expected = "4CFD0CF9-8742-4A83-B3A8-D3F051953CED";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, expected);
        AtomicReference<String> duringRequest = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                duringRequest.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME))
                .isEqualTo(expected.toLowerCase());
        assertThat(duringRequest).hasValue(expected.toLowerCase());
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesInvalidOrNonV4Values() throws Exception {
        for (String invalid : new String[] {
            "not-a-uuid", "550e8400-e29b-11d4-a716-446655440000", "4cfd0cf9-8742-4a83-73a8-d3f051953ced"
        }) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader(CorrelationIdFilter.HEADER_NAME, invalid);
            String actual = invoke(request);

            assertUuidV4(actual);
            assertThat(actual).isNotEqualTo(invalid);
        }
    }

    @Test
    void clearsMdcWhenDownstreamThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
                    assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNotBlank();
                    throw new ServletException("expected test failure");
                }))
                .isInstanceOf(ServletException.class);
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isNotBlank();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void doesNotLeakCorrelationBetweenSequentialRequests() throws Exception {
        MockHttpServletRequest first = new MockHttpServletRequest();
        first.addHeader(CorrelationIdFilter.HEADER_NAME, "4cfd0cf9-8742-4a83-b3a8-d3f051953ced");
        String firstId = invoke(first);
        String secondId = invoke(new MockHttpServletRequest());

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void isolatesConcurrentRequests() throws Exception {
        String firstId = "4cfd0cf9-8742-4a83-b3a8-d3f051953ced";
        String secondId = "09e2887b-6d32-4c08-9e6b-e85a7ff76df2";
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> first = executor.submit(() -> invokeConcurrently(firstId, entered, release));
            Future<String> second = executor.submit(() -> invokeConcurrently(secondId, entered, release));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }

            assertThat(first.get()).isEqualTo(firstId);
            assertThat(second.get()).isEqualTo(secondId);
        }
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    private String invoke(MockHttpServletRequest request) throws ServletException, IOException {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNotBlank());
        return response.getHeader(CorrelationIdFilter.HEADER_NAME);
    }

    private String invokeConcurrently(String correlationId, CountDownLatch entered, CountDownLatch release)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(correlationId);
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ServletException(exception);
            }
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo(correlationId);
        });
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        return response.getHeader(CorrelationIdFilter.HEADER_NAME);
    }

    private static void assertUuidV4(String value) {
        UUID parsed = UUID.fromString(value);
        assertThat(parsed.version()).isEqualTo(4);
        assertThat(parsed.variant()).isEqualTo(2);
    }
}
