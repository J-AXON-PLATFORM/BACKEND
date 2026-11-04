package com.jaxon.ai;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class AiIndexingService {

    private final EmbeddingProvider provider;
    private final DocumentStorage storage;
    private final AiSourceChunker chunker;
    private final AiIndexingPersistence indexing;
    private final Clock clock;

    AiIndexingService(
            EmbeddingProvider provider,
            DocumentStorage storage,
            AiSourceChunker chunker,
            AiIndexingPersistence indexing,
            Clock clock) {
        this.provider = provider;
        this.storage = storage;
        this.chunker = chunker;
        this.indexing = indexing;
        this.clock = clock;
    }

    AiSourceResponse request(
            UUID sourceId, long expectedVersion, UUID actorId, UUID correlationId) {
        return indexing.request(
                sourceId, expectedVersion, actorId, correlationId, provider.isAvailable(), now());
    }

    ProcessOutcome process(AiIndexingPersistence.ClaimedJob job, int batchSize) {
        try {
            byte[] original = storage.readVerified(job.storageKey(), job.sizeBytes(), job.sha256());
            String content = decodeUtf8(original);
            List<AiSourceChunker.Chunk> chunks = chunker.chunk(content);
            List<AiIndexingPersistence.IndexedChunk> indexed = new ArrayList<>(chunks.size());
            for (int offset = 0; offset < chunks.size(); offset += batchSize) {
                List<AiSourceChunker.Chunk> batch = chunks.subList(
                        offset, Math.min(offset + batchSize, chunks.size()));
                List<float[]> embeddings = provider.embedPassages(
                        batch.stream().map(AiSourceChunker.Chunk::content).toList());
                if (embeddings.size() != batch.size()) throw new EmbeddingProviderUnavailableException();
                for (int index = 0; index < batch.size(); index++) {
                    AiSourceChunker.Chunk chunk = batch.get(index);
                    indexed.add(new AiIndexingPersistence.IndexedChunk(
                            chunk.ordinal(),
                            chunk.content(),
                            chunk.sha256(),
                            chunk.tokenCount(),
                            chunk.startToken(),
                            chunk.endToken(),
                            embeddings.get(index)));
                }
            }
            return indexing.complete(job, indexed, now())
                    ? ProcessOutcome.SUCCEEDED
                    : ProcessOutcome.DISCARDED;
        } catch (DocumentStorageUnavailableException exception) {
            return failOrDiscard(job, "SOURCE_ORIGINAL_INVALID");
        } catch (EmptyAiSourceException exception) {
            return failOrDiscard(job, "SOURCE_EMPTY");
        } catch (EmbeddingProviderUnavailableException exception) {
            return failOrDiscard(job, "EMBEDDING_FAILED");
        } catch (RuntimeException exception) {
            return failOrDiscard(job, "INDEXING_FAILED");
        }
    }

    private ProcessOutcome failOrDiscard(AiIndexingPersistence.ClaimedJob job, String failureCode) {
        return indexing.fail(job, failureCode, now())
                ? ProcessOutcome.FAILED
                : ProcessOutcome.DISCARDED;
    }

    private static String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new DocumentStorageUnavailableException();
        }
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    enum ProcessOutcome {
        SUCCEEDED,
        FAILED,
        DISCARDED
    }
}
