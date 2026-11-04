package com.jaxon.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AiSourceChunkerTest {

    private final AiSourceChunker chunker = new AiSourceChunker(new WhitespaceTokenizer());

    @Test
    void createsDeterministicChunksWithTheExactMaximumAndOverlap() {
        String content = words(600);

        List<AiSourceChunker.Chunk> chunks = chunker.chunk(content);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0))
                .extracting(
                        AiSourceChunker.Chunk::ordinal,
                        AiSourceChunker.Chunk::tokenCount,
                        AiSourceChunker.Chunk::startToken,
                        AiSourceChunker.Chunk::endToken)
                .containsExactly(0, 480, 0, 480);
        assertThat(chunks.get(1))
                .extracting(
                        AiSourceChunker.Chunk::ordinal,
                        AiSourceChunker.Chunk::tokenCount,
                        AiSourceChunker.Chunk::startToken,
                        AiSourceChunker.Chunk::endToken)
                .containsExactly(1, 192, 408, 600);
        assertThat(chunks).isEqualTo(chunker.chunk(content));
        assertThat(chunks.get(0).sha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void prefersAParagraphBoundaryWithoutBreakingUnicodeOffsets() {
        String content = words(300) + "\n\nárbol niño " + words(298);

        List<AiSourceChunker.Chunk> chunks = chunker.chunk(content);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).endToken()).isEqualTo(300);
        assertThat(chunks.get(1).startToken()).isEqualTo(228);
        assertThat(chunks.get(1).content()).contains("árbol niño");
    }

    @Test
    void rejectsContentWithoutTokens() {
        assertThatThrownBy(() -> chunker.chunk(" \n\t "))
                .isInstanceOf(EmptyAiSourceException.class);
    }

    private static String words(int count) {
        StringBuilder content = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0) content.append(' ');
            content.append("word").append(index);
        }
        return content.toString();
    }

    private static final class WhitespaceTokenizer implements EmbeddingProvider {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TokenSequence tokenize(String text) {
            Matcher matcher = Pattern.compile("\\S+").matcher(text);
            List<Long> identifiers = new ArrayList<>();
            List<TokenSpan> spans = new ArrayList<>();
            while (matcher.find()) {
                identifiers.add((long) identifiers.size());
                spans.add(new TokenSpan(matcher.start(), matcher.end()));
            }
            long[] ids = new long[identifiers.size()];
            for (int index = 0; index < identifiers.size(); index++) ids[index] = identifiers.get(index);
            return new TokenSequence(ids, spans);
        }

        @Override
        public List<float[]> embedPassages(List<String> contents) {
            throw new UnsupportedOperationException();
        }

        @Override
        public float[] embedQuery(String query) {
            throw new UnsupportedOperationException();
        }
    }
}
