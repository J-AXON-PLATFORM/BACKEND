package com.jaxon.discovery;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class DiscoveryJobFilePublisher {

    private final Path root;

    DiscoveryJobFilePublisher(@Value("${jaxon.discovery.jobs.publication-root:}") String root) {
        this.root = root.isBlank() ? null : Path.of(root).toAbsolutePath().normalize();
    }

    Instant publish(
            UUID executorId, UUID jobId, byte[] document, Instant expiresAt, Clock clock) {
        if (root == null) throw new DiscoveryJobPublicationUnavailableException();
        Path directory = root.resolve("discovery-executor-" + executorId + "-jobs").normalize();
        Path temporary = directory.resolve(jobId + ".json.part").normalize();
        Path target = directory.resolve(jobId + ".json").normalize();
        boolean created = false;
        try {
            requireDirectory(root, root);
            requireDirectory(root, directory);
            if (!temporary.startsWith(directory) || !target.startsWith(directory)) {
                throw new DiscoveryJobPublicationUnavailableException();
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (Arrays.equals(readRegular(target), document)) {
                    Instant publishedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
                    if (!publishedAt.isBefore(expiresAt)) {
                        deleteDurably(target, directory);
                        throw new DiscoveryJobPublicationExpiredException();
                    }
                    return publishedAt;
                }
                throw new DiscoveryJobPublicationConflictException();
            }
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
                readRegular(temporary);
                Files.delete(temporary);
            }
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                created = true;
                ByteBuffer buffer = ByteBuffer.wrap(document);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (!Arrays.equals(readRegular(temporary), document)) {
                throw new DiscoveryJobPublicationUnavailableException();
            }
            Instant publishedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            if (!publishedAt.isBefore(expiresAt)) {
                throw new DiscoveryJobPublicationExpiredException();
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new DiscoveryJobPublicationUnavailableException();
            }
            created = false;
            try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
                channel.force(true);
            }
            Instant completedAt = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
            if (!completedAt.isBefore(expiresAt)) {
                deleteDurably(target, directory);
                throw new DiscoveryJobPublicationExpiredException();
            }
            if (!Arrays.equals(readRegular(target), document)
                    || Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
                throw new DiscoveryJobPublicationUnavailableException();
            }
            return completedAt;
        } catch (DiscoveryJobPublicationConflictException
                | DiscoveryJobPublicationUnavailableException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw new DiscoveryJobPublicationUnavailableException();
        } finally {
            if (created) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Reconciliation only revisits this job's deterministic partial path.
                }
            }
        }
    }

    void removeExpired(UUID executorId, UUID jobId) {
        if (root == null) throw new DiscoveryJobPublicationUnavailableException();
        Path directory = root.resolve("discovery-executor-" + executorId + "-jobs").normalize();
        Path temporary = directory.resolve(jobId + ".json.part").normalize();
        Path target = directory.resolve(jobId + ".json").normalize();
        try {
            requireDirectory(root, root);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) return;
            requireDirectory(root, directory);
            boolean deleted = deleteRegularIfExists(target);
            deleted |= deleteRegularIfExists(temporary);
            if (deleted) {
                try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
                    channel.force(true);
                }
            }
        } catch (DiscoveryJobPublicationConflictException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw new DiscoveryJobPublicationUnavailableException();
        }
    }

    private static void requireDirectory(Path root, Path directory) {
        if (!directory.startsWith(root) || Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new DiscoveryJobPublicationUnavailableException();
        }
    }

    private static byte[] readRegular(Path file) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() > DiscoveryJobContract.MAX_JOB_BYTES
                || Files.isSymbolicLink(file)) {
            throw new DiscoveryJobPublicationConflictException();
        }
        return Files.readAllBytes(file);
    }

    private static void deleteDurably(Path target, Path directory) throws IOException {
        Files.delete(target);
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static boolean deleteRegularIfExists(Path target) throws IOException {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return false;
        readRegular(target);
        Files.delete(target);
        return true;
    }
}

final class DiscoveryJobPublicationUnavailableException extends RuntimeException {}

final class DiscoveryJobPublicationConflictException extends RuntimeException {}

final class DiscoveryJobPublicationExpiredException extends RuntimeException {}
