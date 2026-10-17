package com.jaxon.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnapshotFileReaderTest {

    @TempDir
    Path root;

    private final SnapshotFileReader reader = new SnapshotFileReader();

    @Test
    void readsStableUtf8RegularFileAndComputesItsDigest() throws Exception {
        Path file = root.resolve("snapshot.json");
        Files.writeString(file, "{\"value\":1}");
        Files.setLastModifiedTime(file, FileTime.from(Instant.now().minusSeconds(5)));

        SnapshotFileReader.ReadSnapshot result = reader.read(root, file, Duration.ofSeconds(2), Instant.now());

        assertThat(result.json()).isEqualTo("{\"value\":1}");
        assertThat(result.size()).isEqualTo(11);
        assertThat(result.sha256()).matches("[0-9a-f]{64}");
    }

    @Test
    void refusesSymlinksOversizedFilesInvalidUtf8AndUnstableFiles() throws Exception {
        Path target = root.resolve("target.json");
        Files.writeString(target, "{}");
        Path link = root.resolve("link.json");
        Files.createSymbolicLink(link, target.getFileName());
        assertThatThrownBy(() -> reader.read(root, link, Duration.ZERO, Instant.now()))
                .isInstanceOf(SnapshotFileReader.UnsafeSnapshotFileException.class);

        Path oversized = root.resolve("oversized.json");
        try (var channel = Files.newByteChannel(oversized, java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(SnapshotFileReader.MAX_BYTES);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {1}));
        }
        assertThatThrownBy(() -> reader.read(root, oversized, Duration.ZERO, Instant.now()))
                .isInstanceOf(SnapshotFileReader.UnsafeSnapshotFileException.class)
                .extracting("reasonCode")
                .isEqualTo("FILE_TOO_LARGE");

        Path invalidUtf8 = root.resolve("invalid.json");
        Files.write(invalidUtf8, new byte[] {(byte) 0xc3, (byte) 0x28});
        assertThatThrownBy(() -> reader.read(root, invalidUtf8, Duration.ZERO, Instant.now()))
                .isInstanceOf(SnapshotFileReader.UnsafeSnapshotFileException.class)
                .extracting("reasonCode")
                .isEqualTo("INVALID_UTF8");

        Path recent = root.resolve("recent.json");
        Files.writeString(recent, "{}");
        assertThatThrownBy(() -> reader.read(root, recent, Duration.ofMinutes(1), Instant.now()))
                .isInstanceOf(SnapshotFileReader.SnapshotNotStableException.class);
    }
}
