package com.jaxon.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
final class AiSourceChunker {

    static final int MAX_CONTENT_TOKENS = 480;
    static final int OVERLAP_TOKENS = 72;
    static final String VERSION = "token-480-overlap-72-v1";

    private final EmbeddingProvider provider;

    AiSourceChunker(EmbeddingProvider provider) {
        this.provider = provider;
    }

    List<Chunk> chunk(String content) {
        EmbeddingProvider.TokenSequence tokenized = provider.tokenize(content);
        List<EmbeddingProvider.TokenSpan> spans = tokenized.spans();
        if (spans.isEmpty()) throw new EmptyAiSourceException();
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        while (start < spans.size()) {
            int end = Math.min(start + MAX_CONTENT_TOKENS, spans.size());
            if (end < spans.size()) end = structuralBoundary(content, spans, start, end);
            int startOffset = spans.get(start).startOffset();
            int endOffset = spans.get(end - 1).endOffset();
            String text = content.substring(startOffset, endOffset);
            chunks.add(new Chunk(
                    chunks.size(), text, sha256(text), end - start, start, end));
            if (end == spans.size()) break;
            int next = end - OVERLAP_TOKENS;
            if (next <= start) throw new IllegalStateException("Chunker did not advance");
            start = next;
        }
        return List.copyOf(chunks);
    }

    private static int structuralBoundary(
            String content, List<EmbeddingProvider.TokenSpan> spans, int start, int maximumEnd) {
        int minimumEnd = start + MAX_CONTENT_TOKENS / 2;
        int minimumOffset = spans.get(minimumEnd - 1).endOffset();
        int maximumOffset = spans.get(maximumEnd - 1).endOffset();
        int paragraph = content.lastIndexOf("\n\n", maximumOffset);
        if (paragraph < minimumOffset) return maximumEnd;
        for (int index = maximumEnd - 1; index >= minimumEnd; index--) {
            if (spans.get(index - 1).endOffset() <= paragraph) return index;
        }
        return maximumEnd;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record Chunk(
            int ordinal,
            String content,
            String sha256,
            int tokenCount,
            int startToken,
            int endToken) {}
}

final class EmptyAiSourceException extends RuntimeException {}
