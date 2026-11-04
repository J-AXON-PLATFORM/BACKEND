package com.jaxon.ai;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.tokenizers.jni.CharSpan;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class LocalOnnxEmbeddingProvider implements EmbeddingProvider, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalOnnxEmbeddingProvider.class);
    private static final long MODEL_SIZE = 470_268_510L;
    private static final String MODEL_SHA256 =
            "ca456c06b3a9505ddfd9131408916dd79290368331e7d76bb621f1cba6bc8665";
    private static final long TOKENIZER_SIZE = 17_082_730L;

    private final TransformersEmbeddingModel model;
    private final HuggingFaceTokenizer tokenizer;
    private final boolean available;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean resourcesClosed = new AtomicBoolean();
    private final ReentrantReadWriteLock lifecycleLock = new ReentrantReadWriteLock();

    LocalOnnxEmbeddingProvider(
            @Value("${jaxon.ai.embedding.model-path:}") String modelPath,
            @Value("${jaxon.ai.embedding.tokenizer-path:}") String tokenizerPath) {
        TransformersEmbeddingModel loadedModel = null;
        HuggingFaceTokenizer loadedTokenizer = null;
        boolean loaded = false;
        try {
            Path verifiedModel = verifyArtifact(modelPath, MODEL_SIZE, MODEL_SHA256);
            Path verifiedTokenizer = verifyArtifact(tokenizerPath, TOKENIZER_SIZE, VOCABULARY_DIGEST);
            Map<String, String> options = Map.of("padding", "true", "truncation", "false");
            loadedTokenizer = HuggingFaceTokenizer.newInstance(verifiedTokenizer, options);
            loadedModel = new TransformersEmbeddingModel();
            loadedModel.setModelResource(verifiedModel.toUri().toString());
            loadedModel.setTokenizerResource(verifiedTokenizer.toUri().toString());
            loadedModel.setTokenizerOptions(options);
            loadedModel.setDisableCaching(true);
            loadedModel.afterPropertiesSet();
            float[] probe = normalize(loadedModel.embed("query: disponibilidad"));
            requireValid(probe);
            loaded = true;
            LOGGER.info("Local embedding provider is available");
        } catch (Exception | LinkageError exception) {
            closeQuietly(loadedModel, loadedTokenizer);
            loadedModel = null;
            loadedTokenizer = null;
            LOGGER.warn("Local embedding provider is unavailable");
        }
        this.model = loadedModel;
        this.tokenizer = loadedTokenizer;
        this.available = loaded;
    }

    @Override
    public boolean isAvailable() {
        return available && !closed.get();
    }

    @Override
    public TokenSequence tokenize(String text) {
        lifecycleLock.readLock().lock();
        try {
            requireAvailable();
            Encoding encoding = tokenizer.encode(text, false, false);
            long[] ids = encoding.getIds();
            CharSpan[] offsets = encoding.getCharTokenSpans();
            if (encoding.exceedMaxLength() || ids.length != offsets.length) {
                throw new EmbeddingProviderUnavailableException();
            }
            List<TokenSpan> spans = new ArrayList<>(offsets.length);
            for (CharSpan offset : offsets) {
                spans.add(new TokenSpan(offset.getStart(), offset.getEnd()));
            }
            return new TokenSequence(ids, spans);
        } finally {
            lifecycleLock.readLock().unlock();
            closeResourcesIfIdle();
        }
    }

    @Override
    public List<float[]> embedPassages(List<String> contents) {
        lifecycleLock.readLock().lock();
        try {
            requireAvailable();
            List<String> instructions = contents.stream().map(content -> "passage: " + content).toList();
            List<float[]> embeddings = model.embed(instructions).stream()
                    .map(LocalOnnxEmbeddingProvider::normalize)
                    .toList();
            embeddings.forEach(LocalOnnxEmbeddingProvider::requireValid);
            return embeddings;
        } finally {
            lifecycleLock.readLock().unlock();
            closeResourcesIfIdle();
        }
    }

    @Override
    public float[] embedQuery(String query) {
        lifecycleLock.readLock().lock();
        try {
            requireAvailable();
            float[] embedding = normalize(model.embed("query: " + query));
            requireValid(embedding);
            return embedding;
        } finally {
            lifecycleLock.readLock().unlock();
            closeResourcesIfIdle();
        }
    }

    @Override
    public void close() {
        closed.set(true);
        closeResourcesIfIdle();
    }

    private void requireAvailable() {
        if (!available || closed.get()) throw new EmbeddingProviderUnavailableException();
    }

    private void closeResourcesIfIdle() {
        if (!closed.get() || resourcesClosed.get() || !lifecycleLock.writeLock().tryLock()) return;
        try {
            if (resourcesClosed.compareAndSet(false, true)) closeQuietly(model, tokenizer);
        } finally {
            lifecycleLock.writeLock().unlock();
        }
    }

    private static Path verifyArtifact(String configuredPath, long expectedSize, String expectedSha256)
            throws IOException {
        if (configuredPath == null || configuredPath.isBlank()) throw new IOException("Missing artifact");
        Path path = Path.of(configuredPath).toAbsolutePath().normalize();
        if (Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) != expectedSize) {
            throw new IOException("Invalid artifact");
        }
        String actualSha256 = sha256(path);
        if (!actualSha256.equals(expectedSha256)) throw new IOException("Invalid artifact");
        return path;
    }

    static String sha256(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static float[] normalize(float[] source) {
        double squared = 0.0;
        for (float value : source) squared += value * value;
        double norm = Math.sqrt(squared);
        if (!Double.isFinite(norm) || norm == 0.0) throw new EmbeddingProviderUnavailableException();
        float[] normalized = Arrays.copyOf(source, source.length);
        for (int index = 0; index < normalized.length; index++) {
            normalized[index] = (float) (normalized[index] / norm);
        }
        return normalized;
    }

    static void requireValid(float[] embedding) {
        if (embedding.length != DIMENSIONS) throw new EmbeddingProviderUnavailableException();
        double squared = 0.0;
        for (float value : embedding) {
            if (!Float.isFinite(value)) throw new EmbeddingProviderUnavailableException();
            squared += value * value;
        }
        if (Math.abs(Math.sqrt(squared) - 1.0) > 1.0e-5) {
            throw new EmbeddingProviderUnavailableException();
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void closeQuietly(TransformersEmbeddingModel model, HuggingFaceTokenizer tokenizer) {
        if (model != null) {
            try {
                model.close();
            } catch (Exception ignored) {
                // The provider is already unavailable and no resource detail is exposed.
            }
        }
        if (tokenizer != null) tokenizer.close();
    }
}
