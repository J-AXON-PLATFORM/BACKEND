package com.jaxon.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiscoveryJobFilePublisherTest {

    private static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir Path root;

    @Test
    void publishesAtomicallyAndAcceptsOnlyIdenticalFinalContent() throws Exception {
        UUID executorId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        byte[] document = "{\"jobId\":\"safe\"}".getBytes();
        Path directory = directory(executorId);
        Files.createDirectory(directory);
        DiscoveryJobFilePublisher publisher = new DiscoveryJobFilePublisher(root.toString());

        assertThat(publisher.publish(executorId, jobId, document, NOW.plusSeconds(1), CLOCK))
                .isEqualTo(NOW);
        assertThat(publisher.publish(executorId, jobId, document, NOW.plusSeconds(1), CLOCK))
                .isEqualTo(NOW);
        assertThat(Files.readAllBytes(directory.resolve(jobId + ".json"))).isEqualTo(document);
        assertThat(directory.resolve(jobId + ".json.part")).doesNotExist();
        assertThatThrownBy(() -> publisher.publish(
                        executorId, jobId, "different".getBytes(), NOW.plusSeconds(1), CLOCK))
                .isInstanceOf(DiscoveryJobPublicationConflictException.class);
    }

    @Test
    void replacesARegularStalePartialButRejectsNonRegularPaths() throws Exception {
        UUID executorId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        byte[] document = "safe".getBytes();
        Path directory = directory(executorId);
        Files.createDirectory(directory);
        Path partial = directory.resolve(jobId + ".json.part");
        Files.writeString(partial, "stale");
        DiscoveryJobFilePublisher publisher = new DiscoveryJobFilePublisher(root.toString());

        publisher.publish(executorId, jobId, document, NOW.plusSeconds(1), CLOCK);
        assertThat(partial).doesNotExist();

        UUID linkedJob = UUID.randomUUID();
        Path outside = Files.writeString(root.resolve("outside"), "unsafe");
        Files.createSymbolicLink(directory.resolve(linkedJob + ".json"), outside);
        assertThatThrownBy(() -> publisher.publish(
                        executorId, linkedJob, document, NOW.plusSeconds(1), CLOCK))
                .isInstanceOf(DiscoveryJobPublicationConflictException.class);
    }

    @Test
    void refusesToRenameAJobAtOrAfterItsExpiryAndCleansThePartial() throws Exception {
        UUID executorId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Path directory = directory(executorId);
        Files.createDirectory(directory);
        DiscoveryJobFilePublisher publisher = new DiscoveryJobFilePublisher(root.toString());

        assertThatThrownBy(() -> publisher.publish(executorId, jobId, "safe".getBytes(), NOW, CLOCK))
                .isInstanceOf(DiscoveryJobPublicationExpiredException.class);
        assertThat(directory.resolve(jobId + ".json")).doesNotExist();
        assertThat(directory.resolve(jobId + ".json.part")).doesNotExist();
    }

    @Test
    void removesAJobThatExpiresWhileTheRenameIsBeingDurablyConfirmed() throws Exception {
        UUID executorId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        Path directory = directory(executorId);
        Files.createDirectory(directory);
        Clock advancingClock = mock(Clock.class);
        when(advancingClock.instant()).thenReturn(NOW, NOW.plusSeconds(2));
        DiscoveryJobFilePublisher publisher = new DiscoveryJobFilePublisher(root.toString());

        assertThatThrownBy(() -> publisher.publish(
                        executorId, jobId, "safe".getBytes(), NOW.plusSeconds(1), advancingClock))
                .isInstanceOf(DiscoveryJobPublicationExpiredException.class);
        assertThat(directory.resolve(jobId + ".json")).doesNotExist();
        assertThat(directory.resolve(jobId + ".json.part")).doesNotExist();
    }

    @Test
    void removesAnIdenticalFinalFileFoundAfterExpiry() throws Exception {
        UUID executorId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        byte[] document = "safe".getBytes();
        Path directory = directory(executorId);
        Files.createDirectory(directory);
        Files.write(directory.resolve(jobId + ".json"), document);
        DiscoveryJobFilePublisher publisher = new DiscoveryJobFilePublisher(root.toString());

        assertThatThrownBy(() -> publisher.publish(executorId, jobId, document, NOW, CLOCK))
                .isInstanceOf(DiscoveryJobPublicationExpiredException.class);
        assertThat(directory.resolve(jobId + ".json")).doesNotExist();
    }

    private Path directory(UUID executorId) {
        return root.resolve("discovery-executor-" + executorId + "-jobs");
    }
}
