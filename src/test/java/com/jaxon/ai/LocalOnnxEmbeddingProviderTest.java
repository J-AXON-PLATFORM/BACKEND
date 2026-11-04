package com.jaxon.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalOnnxEmbeddingProviderTest {

    @TempDir Path temporaryDirectory;

    @Test
    void hashesArtifactsThroughAStream() throws Exception {
        Path artifact = Files.writeString(temporaryDirectory.resolve("artifact.bin"), "bounded hash");

        assertThat(LocalOnnxEmbeddingProvider.sha256(artifact))
                .isEqualTo("b98e30c36ce80c8fb8eaa857febbfb5f404c094f9598b4495822d6e8dc04c9ba");
    }

    @Test
    void normalizesFiniteEmbeddingsAndRejectsInvalidDimensions() {
        float[] source = new float[EmbeddingProvider.DIMENSIONS];
        source[0] = 3.0f;
        source[1] = 4.0f;

        float[] normalized = LocalOnnxEmbeddingProvider.normalize(source);

        assertThat(normalized[0]).isEqualTo(0.6f);
        assertThat(normalized[1]).isEqualTo(0.8f);
        assertThat(source[0]).isEqualTo(3.0f);
        LocalOnnxEmbeddingProvider.requireValid(normalized);
        assertThatThrownBy(() -> LocalOnnxEmbeddingProvider.requireValid(new float[2]))
                .isInstanceOf(EmbeddingProviderUnavailableException.class);
        assertThatThrownBy(() -> LocalOnnxEmbeddingProvider.normalize(new float[EmbeddingProvider.DIMENSIONS]))
                .isInstanceOf(EmbeddingProviderUnavailableException.class);
    }
}
