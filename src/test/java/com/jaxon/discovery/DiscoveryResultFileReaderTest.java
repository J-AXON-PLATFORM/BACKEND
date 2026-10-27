package com.jaxon.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscoveryResultFileReaderTest {

    private final DiscoveryResultFileReader reader = new DiscoveryResultFileReader();

    @Test
    void readsStableRegularUtf8AndRejectsInvalidUtf8(@TempDir Path root) throws Exception {
        Path valid = Files.writeString(root.resolve("valid.json"), "{}");

        DiscoveryResultFileReader.ReadResult result =
                reader.read(root, valid, Duration.ZERO, Instant.now().plusSeconds(1));

        assertThat(result.json()).isEqualTo("{}");
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.sha256()).hasSize(64);

        Path invalid = Files.write(root.resolve("invalid.json"), new byte[] {(byte) 0xc3, (byte) 0x28});
        assertThatThrownBy(() -> reader.read(
                        root, invalid, Duration.ZERO, Instant.now().plusSeconds(1)))
                .isInstanceOfSatisfying(
                        DiscoveryResultFileReader.UnsafeResultFileException.class,
                        exception -> assertThat(exception.reasonCode()).isEqualTo("INVALID_UTF8"));
    }

    @Test
    void rejectsUnstableOversizedAndSymbolicFiles(@TempDir Path root) throws Exception {
        Path unstable = Files.writeString(root.resolve("unstable.json"), "{}");
        assertThatThrownBy(() -> reader.read(root, unstable, Duration.ofHours(1), Instant.now()))
                .isInstanceOf(DiscoveryResultFileReader.ResultNotStableException.class);

        Path oversized = Files.write(root.resolve("oversized.json"),
                new byte[DiscoveryResultContract.MAX_RESULT_BYTES + 1]);
        assertThatThrownBy(() -> reader.read(
                        root, oversized, Duration.ZERO, Instant.now().plusSeconds(1)))
                .isInstanceOfSatisfying(
                        DiscoveryResultFileReader.UnsafeResultFileException.class,
                        exception -> assertThat(exception.reasonCode()).isEqualTo("FILE_TOO_LARGE"));

        Path target = Files.writeString(root.resolve("target.json"), "{}");
        Path link = Files.createSymbolicLink(root.resolve("link.json"), target.getFileName());
        assertThatThrownBy(() -> reader.read(root, link, Duration.ZERO, Instant.now().plusSeconds(1)))
                .isInstanceOfSatisfying(
                        DiscoveryResultFileReader.UnsafeResultFileException.class,
                        exception -> assertThat(exception.reasonCode()).isEqualTo("FILE_UNSAFE"));

        Path external = Files.createTempDirectory("discovery-results-external");
        try {
            Files.writeString(external.resolve("result.json"), "{}");
            Path linkedMailbox = Files.createSymbolicLink(root.resolve("mailbox"), external);
            assertThatThrownBy(() -> reader.read(
                            root, linkedMailbox.resolve("result.json"),
                            Duration.ZERO, Instant.now().plusSeconds(1)))
                    .isInstanceOfSatisfying(
                            DiscoveryResultFileReader.UnsafeResultFileException.class,
                            exception -> assertThat(exception.reasonCode()).isEqualTo("FILE_UNSAFE"));
        } finally {
            Files.deleteIfExists(external.resolve("result.json"));
            Files.deleteIfExists(external);
        }
    }

    @Test
    void refusesToClaimThroughASymbolicMailbox(@TempDir Path root) throws Exception {
        Path external = Files.createTempDirectory("discovery-claim-external");
        try {
            Files.writeString(external.resolve("result.json"), "{}");
            Files.createSymbolicLink(root.resolve("mailbox"), external);

            assertThatThrownBy(() -> reader.claim(
                            root, "mailbox", "result.json", "result.claim.json"))
                    .isInstanceOf(java.io.IOException.class);
            assertThat(external.resolve("result.json")).isRegularFile();
        } finally {
            Files.deleteIfExists(root.resolve(".jaxon-claims"));
            Files.deleteIfExists(root.resolve("mailbox"));
            Files.deleteIfExists(external.resolve("result.json"));
            Files.deleteIfExists(external);
        }
    }

    @Test
    void refusesSymbolicRootAndClaimDirectory(@TempDir Path parent) throws Exception {
        Path external = Files.createTempDirectory("discovery-root-external");
        Path linkedRoot = Files.createSymbolicLink(parent.resolve("linked-root"), external);
        Path realRoot = Files.createDirectory(parent.resolve("real-root"));
        Path mailbox = Files.createDirectory(realRoot.resolve("mailbox"));
        Files.writeString(mailbox.resolve("result.json"), "{}");
        Path externalClaims = Files.createDirectory(external.resolve("external-claims"));
        Files.createSymbolicLink(realRoot.resolve(".jaxon-claims"), externalClaims);
        try {
            assertThatThrownBy(() -> reader.claim(
                            linkedRoot, "mailbox", "result.json", "result.claim.json"))
                    .isInstanceOf(java.io.IOException.class);
            assertThat(external.resolve(".jaxon-claims")).doesNotExist();
            assertThatThrownBy(() -> reader.claim(
                            realRoot, "mailbox", "result.json", "result.claim.json"))
                    .isInstanceOf(java.io.IOException.class);
            assertThat(mailbox.resolve("result.json")).isRegularFile();
        } finally {
            Files.deleteIfExists(realRoot.resolve(".jaxon-claims"));
            Files.deleteIfExists(mailbox.resolve("result.json"));
            Files.deleteIfExists(mailbox);
            Files.deleteIfExists(realRoot);
            Files.deleteIfExists(linkedRoot);
            Files.deleteIfExists(externalClaims);
            Files.deleteIfExists(external);
        }
    }
}
