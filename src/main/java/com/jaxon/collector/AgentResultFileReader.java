package com.jaxon.collector;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class AgentResultFileReader {

    ReadResult read(Path root, Path file, Duration stabilityAge, Instant now) {
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path normalizedFile = file.toAbsolutePath().normalize();
            if (!normalizedFile.startsWith(normalizedRoot) || Files.isSymbolicLink(normalizedFile)) {
                throw new UnsafeResultFileException("FILE_UNSAFE");
            }
            BasicFileAttributes before = Files.readAttributes(
                    normalizedFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!before.isRegularFile()) {
                throw new UnsafeResultFileException("FILE_UNSAFE");
            }
            if (before.size() > AgentTaskContract.MAX_RESULT_BYTES) {
                throw new UnsafeResultFileException("FILE_TOO_LARGE");
            }
            if (before.lastModifiedTime().toInstant().plus(stabilityAge).isAfter(now)) {
                throw new ResultNotStableException();
            }
            byte[] bytes;
            try (SeekableByteChannel channel = Files.newByteChannel(
                    normalizedFile,
                    Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                ByteArrayOutputStream output = new ByteArrayOutputStream((int) before.size());
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                long total = 0;
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    total += buffer.remaining();
                    if (total > AgentTaskContract.MAX_RESULT_BYTES) {
                        throw new UnsafeResultFileException("FILE_TOO_LARGE");
                    }
                    output.write(buffer.array(), buffer.position(), buffer.remaining());
                    buffer.clear();
                }
                bytes = output.toByteArray();
            }
            BasicFileAttributes after = Files.readAttributes(
                    normalizedFile, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!after.isRegularFile()
                    || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !java.util.Objects.equals(before.fileKey(), after.fileKey())) {
                throw new ResultNotStableException();
            }
            String json;
            try {
                json = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes))
                        .toString();
            } catch (CharacterCodingException exception) {
                throw new UnsafeResultFileException("INVALID_UTF8");
            }
            return new ReadResult(json, sha256(bytes), bytes.length);
        } catch (ResultNotStableException | UnsafeResultFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new UnsafeResultFileException("FILE_UNSAFE");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record ReadResult(String json, String sha256, long size) {}

    static final class ResultNotStableException extends RuntimeException {}

    static final class UnsafeResultFileException extends RuntimeException {
        private final String reasonCode;

        UnsafeResultFileException(String reasonCode) {
            this.reasonCode = reasonCode;
        }

        String reasonCode() {
            return reasonCode;
        }
    }
}
