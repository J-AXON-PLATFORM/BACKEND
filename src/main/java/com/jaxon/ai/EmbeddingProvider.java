package com.jaxon.ai;

import java.util.List;

interface EmbeddingProvider {

    String MODEL_ID = "intfloat/multilingual-e5-small";
    String MODEL_REVISION = "614241f622f53c4eeff9890bdc4f31cfecc418b3";
    String VOCABULARY_DIGEST = "0b44a9d7b51c3c62626640cda0e2c2f70fdacdc25bbbd68038369d14ebdf4c39";
    int DIMENSIONS = 384;

    boolean isAvailable();

    TokenSequence tokenize(String text);

    List<float[]> embedPassages(List<String> contents);

    float[] embedQuery(String query);

    record TokenSequence(long[] ids, List<TokenSpan> spans) {
        public TokenSequence {
            ids = ids.clone();
            spans = List.copyOf(spans);
            if (ids.length != spans.size()) throw new IllegalArgumentException("Token metadata mismatch");
        }

        @Override
        public long[] ids() {
            return ids.clone();
        }
    }

    record TokenSpan(int startOffset, int endOffset) {}
}

final class EmbeddingProviderUnavailableException extends RuntimeException {}
