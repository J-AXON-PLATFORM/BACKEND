package com.jaxon.ai;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
class AiRetrievalService {

    static final int MAX_QUERY_TOKENS = 480;

    private final EmbeddingProvider provider;
    private final AiRetrievalPersistence persistence;
    private final MeterRegistry registry;

    AiRetrievalService(
            EmbeddingProvider provider, AiRetrievalPersistence persistence, MeterRegistry registry) {
        this.provider = provider;
        this.persistence = persistence;
        this.registry = registry;
    }

    AiRetrievalResponse retrieve(String query, int limit) {
        long started = System.nanoTime();
        String outcome = "unavailable";
        try {
            if (!provider.isAvailable()) throw new EmbeddingProviderUnavailableException();
            int tokens = provider.tokenize(query).ids().length;
            if (tokens == 0) {
                outcome = "rejected";
                throw new InvalidAiRetrievalException("query", "BLANK");
            }
            if (tokens > MAX_QUERY_TOKENS) {
                outcome = "rejected";
                throw new InvalidAiRetrievalException("query", "TOO_MANY_TOKENS");
            }
            float[] embedding = provider.embedQuery(query);
            if (embedding == null || embedding.length != EmbeddingProvider.DIMENSIONS) {
                throw new EmbeddingProviderUnavailableException();
            }
            for (float value : embedding) {
                if (!Float.isFinite(value)) throw new EmbeddingProviderUnavailableException();
            }
            outcome = "failed";
            List<AiRetrievalResult> results = persistence.search(embedding, limit);
            outcome = results.isEmpty() ? "empty" : "hit";
            return new AiRetrievalResponse(limit, results);
        } finally {
            Timer.builder("jaxon.ai.retrieval.duration")
                    .tag("outcome", outcome)
                    .register(registry)
                    .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }
}

final class InvalidAiRetrievalException extends RuntimeException {
    private final String field;
    private final String errorCode;

    InvalidAiRetrievalException(String field, String errorCode) {
        this.field = field;
        this.errorCode = errorCode;
    }

    String field() {
        return field;
    }

    String errorCode() {
        return errorCode;
    }
}
