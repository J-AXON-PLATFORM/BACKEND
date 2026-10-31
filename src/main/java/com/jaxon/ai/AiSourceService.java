package com.jaxon.ai;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
class AiSourceService {

    private final DocumentStorage storage;
    private final AiSourcePersistence persistence;
    private final Clock clock;

    AiSourceService(DocumentStorage storage, AiSourcePersistence persistence, Clock clock) {
        this.storage = storage;
        this.persistence = persistence;
        this.clock = clock;
    }

    AiSourceResponse create(
            String requestedName, MultipartFile file, UUID actorId, UUID correlationId) {
        String name = normalizeName(requestedName);
        if (file == null || file.getContentType() == null
                || !"text/plain".equalsIgnoreCase(file.getContentType())) {
            throw new InvalidAiSourceException("file", "UNSUPPORTED_MEDIA_TYPE");
        }
        if (file.getSize() > FileSystemDocumentStorage.MAX_BYTES) {
            throw new DocumentTooLargeException();
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new DocumentStorageUnavailableException();
        }
        if (content.length > FileSystemDocumentStorage.MAX_BYTES) {
            throw new DocumentTooLargeException();
        }
        requireUtf8(content);

        DocumentStorage.StoredDocument stored = storage.store(new ByteArrayInputStream(content));
        try {
            return persistence.create(
                    UUID.randomUUID(), name, stored, actorId, correlationId, now());
        } catch (RuntimeException exception) {
            try {
                storage.delete(stored.storageKey());
            } catch (DocumentStorageUnavailableException ignored) {
                // The file is not published in metadata and remains an allowed orphan.
            }
            throw exception;
        }
    }

    AiSourcePage findPage(int page, int size) {
        return persistence.findPage(page, size);
    }

    Optional<AiSourceResponse> find(UUID sourceId) {
        return persistence.find(sourceId);
    }

    private static String normalizeName(String input) {
        if (input == null) throw new InvalidAiSourceException("name", "INVALID_FIELD");
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replaceAll("(?U)\\s+", " ")
                .trim();
        if (normalized.isEmpty() || normalized.codePointCount(0, normalized.length()) > 256) {
            throw new InvalidAiSourceException("name", "INVALID_FIELD");
        }
        return normalized;
    }

    private static void requireUtf8(byte[] content) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content));
        } catch (CharacterCodingException exception) {
            throw new InvalidAiSourceException("file", "INVALID_UTF8");
        }
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }
}

final class InvalidAiSourceException extends RuntimeException {
    private final String field;
    private final String errorCode;

    InvalidAiSourceException(String field, String errorCode) {
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
