package com.jaxon.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiRetrievalPersistenceTest {

    @Test
    void acceptsOnlyFiniteBoundedSimilarities() {
        assertThat(AiRetrievalPersistence.finite(1.0)).isEqualTo(1.0);
        assertThat(AiRetrievalPersistence.finite(-1.0)).isEqualTo(-1.0);
        assertThat(AiRetrievalPersistence.finite(0.25)).isEqualTo(0.25);
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 1.0000001, -1.0000001}) {
            assertThatThrownBy(() -> AiRetrievalPersistence.finite(invalid))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void trimsUnicodeBlankCharactersFromTheQuery() {
        assertThat(AiRetrievalController.trimBlank("  red \t\n")).isEqualTo("red");
        assertThat(AiRetrievalController.trimBlank("\u00A0\uFEFF\u2003red\u001F")).isEqualTo("red");
        assertThat(AiRetrievalController.trimBlank("\u00A0\uFEFF\u2003\u001F")).isEmpty();
        assertThat(AiRetrievalController.trimBlank("\u200B\u2060red\u200D")).isEqualTo("red");
        assertThat(AiRetrievalController.trimBlank("red  green")).isEqualTo("red  green");
    }
}
