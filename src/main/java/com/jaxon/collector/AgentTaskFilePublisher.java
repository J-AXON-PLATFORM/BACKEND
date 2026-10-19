package com.jaxon.collector;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class AgentTaskFilePublisher {

    private final Path root;

    AgentTaskFilePublisher(@Value("${jaxon.collector.tasks.publication-root:}") String root) {
        this.root = root.isBlank() ? null : Path.of(root).toAbsolutePath().normalize();
    }

    void publish(UUID agentId, UUID taskId, byte[] document) {
        if (root == null) {
            throw new TaskPublicationUnavailableException();
        }
        Path directory = root.resolve("agent-" + agentId + "-tasks").normalize();
        Path temporary = directory.resolve(taskId + ".json.part").normalize();
        Path target = directory.resolve(taskId + ".json").normalize();
        boolean created = false;
        try {
            requireDirectory(root, root);
            requireDirectory(root, directory);
            requireContained(directory, temporary);
            requireContained(directory, target);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                if (Arrays.equals(readRegular(target, AgentTaskContract.MAX_TASK_BYTES), document)) {
                    return;
                }
                throw new TaskPublicationConflictException();
            }
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
                readRegular(temporary, AgentTaskContract.MAX_TASK_BYTES);
                Files.delete(temporary);
            }
            try (FileChannel channel = FileChannel.open(
                    temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                created = true;
                ByteBuffer buffer = ByteBuffer.wrap(document);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            if (!Arrays.equals(readRegular(temporary, AgentTaskContract.MAX_TASK_BYTES), document)) {
                throw new TaskPublicationUnavailableException();
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new TaskPublicationUnavailableException();
            }
            created = false;
            forceDirectory(directory);
            if (!Arrays.equals(readRegular(target, AgentTaskContract.MAX_TASK_BYTES), document)
                    || Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) {
                throw new TaskPublicationUnavailableException();
            }
        } catch (TaskPublicationConflictException | TaskPublicationUnavailableException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw new TaskPublicationUnavailableException();
        } finally {
            if (created) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // A later reconciliation validates and replaces only this task's known partial path.
                }
            }
        }
    }

    private static void requireDirectory(Path root, Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)
                || Files.isSymbolicLink(normalized)
                || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new TaskPublicationUnavailableException();
        }
    }

    private static void requireContained(Path directory, Path file) {
        if (!file.startsWith(directory) || Files.isSymbolicLink(file)) {
            throw new TaskPublicationUnavailableException();
        }
    }

    private static byte[] readRegular(Path file, int maximum) throws IOException {
        BasicFileAttributes attributes =
                Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() > maximum || Files.isSymbolicLink(file)) {
            throw new TaskPublicationConflictException();
        }
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > maximum) {
            throw new TaskPublicationConflictException();
        }
        return bytes;
    }

    private static void forceDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }
}

final class TaskPublicationUnavailableException extends RuntimeException {}

final class TaskPublicationConflictException extends RuntimeException {}
