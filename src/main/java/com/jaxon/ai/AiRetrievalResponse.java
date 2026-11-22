package com.jaxon.ai;

import java.util.List;
import java.util.UUID;

record AiRetrievalResponse(int limit, List<AiRetrievalResult> results) {

    AiRetrievalResponse {
        results = List.copyOf(results);
    }
}

record AiRetrievalResult(
        UUID sourceId,
        String sourceName,
        String sourceSha256,
        UUID chunkId,
        int ordinal,
        int startToken,
        int endToken,
        String contentSha256,
        String content,
        double similarity) {}
