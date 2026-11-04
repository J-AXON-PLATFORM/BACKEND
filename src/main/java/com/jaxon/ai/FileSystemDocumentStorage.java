package com.jaxon.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class FileSystemDocumentStorage implements DocumentStorage {

    static final long MAX_BYTES = 1024L * 1024L;
    private static final Pattern STORAGE_KEY = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.txt$");
    private static final byte[] EMPTY = new byte[0];

    private final Path root;
    private final Supplier<UUID> uuids;

    @Autowired
    FileSystemDocumentStorage(@Value("${jaxon.ai.document-storage.root:}") String root) {
        this(root, UUID::randomUUID);
    }

    FileSystemDocumentStorage(String root, Supplier<UUID> uuids) {
        this.root = root.isBlank() ? null : Path.of(root).toAbsolutePath().normalize();
        this.uuids = uuids;
    }

    @Override
    public StoredDocument store(InputStream content) {
        requireRoot();
        String storageKey = uuids.get() + ".txt";
        Path temporary = root.resolve("." + uuids.get() + ".part").normalize();
        Path target = root.resolve(storageKey).normalize();
        boolean temporaryCreated = false;
        boolean targetCreated = false;
        boolean completed = false;
        try {
            requireContained(temporary);
            requireContained(target);
            MessageDigest writtenDigest = sha256();
            long written = 0;
            try (FileChannel channel = FileChannel.open(
                            temporary,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE,
                            LinkOption.NOFOLLOW_LINKS);
                    InputStream input = content) {
                temporaryCreated = true;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    written += read;
                    if (written > MAX_BYTES) throw new DocumentTooLargeException();
                    writtenDigest.update(buffer, 0, read);
                    ByteBuffer bytes = ByteBuffer.wrap(buffer, 0, read);
                    while (bytes.hasRemaining()) channel.write(bytes);
                }
                channel.force(true);
            }
            requirePrivateRegularFile(temporary, written);
            try (FileChannel ignored = FileChannel.open(
                    target,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS)) {
                targetCreated = true;
            }
            requirePrivateRegularFile(target, 0L);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new DocumentStorageUnavailableException();
            }
            temporaryCreated = false;
            forceDirectory();

            StoredDocument stored = inspect(target, storageKey);
            if (stored.sizeBytes() != written
                    || !stored.sha256().equals(HexFormat.of().formatHex(writtenDigest.digest()))) {
                throw new DocumentStorageUnavailableException();
            }
            completed = true;
            return stored;
        } catch (DocumentTooLargeException | DocumentStorageUnavailableException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw new DocumentStorageUnavailableException();
        } finally {
            if (temporaryCreated) deleteQuietly(temporary);
            if (targetCreated && !completed) deleteQuietly(target);
        }
    }

    @Override
    public byte[] readVerified(String storageKey, long expectedSize, String expectedSha256) {
        requireRoot();
        if (!STORAGE_KEY.matcher(storageKey).matches()
                || expectedSize < 0
                || expectedSize > MAX_BYTES
                || expectedSha256 == null
                || !expectedSha256.matches("^[0-9a-f]{64}$")) {
            throw new DocumentStorageUnavailableException();
        }
        Path target = root.resolve(storageKey).normalize();
        requireContained(target);
        try {
            requirePrivateRegularFile(target, expectedSize);
            byte[] content;
            try (InputStream input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
                content = input.readNBytes(Math.toIntExact(MAX_BYTES + 1));
            }
            if (content.length != expectedSize
                    || !HexFormat.of().formatHex(sha256().digest(content)).equals(expectedSha256)) {
                throw new DocumentStorageUnavailableException();
            }
            return content;
        } catch (IOException | SecurityException exception) {
            throw new DocumentStorageUnavailableException();
        }
    }

    @Override
    public void delete(String storageKey) {
        requireRoot();
        if (!STORAGE_KEY.matcher(storageKey).matches()) throw new DocumentStorageUnavailableException();
        Path target = root.resolve(storageKey).normalize();
        requireContained(target);
        try {
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return;
            requirePrivateRegularFile(target, null);
            Files.delete(target);
            forceDirectory();
        } catch (IOException | SecurityException exception) {
            throw new DocumentStorageUnavailableException();
        }
    }

    private StoredDocument inspect(Path target, String storageKey) {
        try {
            requirePrivateRegularFile(target, null);
            MessageDigest digest = sha256();
            long size = 0;
            try (InputStream input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    size += read;
                    if (size > MAX_BYTES) throw new DocumentStorageUnavailableException();
                    digest.update(buffer, 0, read);
                }
            }
            return new StoredDocument(storageKey, size, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | SecurityException exception) {
            throw new DocumentStorageUnavailableException();
        }
    }

    private void requireRoot() {
        if (root == null || Files.isSymbolicLink(root)) throw new DocumentStorageUnavailableException();
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    root, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory()
                    || Files.getFileAttributeView(root, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                            == null) {
                throw new DocumentStorageUnavailableException();
            }
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
        } catch (IOException | SecurityException exception) {
            throw new DocumentStorageUnavailableException();
        }
    }

    private void requireContained(Path path) {
        if (!path.startsWith(root) || path.getParent() == null || !path.getParent().equals(root)) {
            throw new DocumentStorageUnavailableException();
        }
    }

    private static void requirePrivateRegularFile(Path path, Long expectedSize) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || Files.isSymbolicLink(path)
                || (expectedSize != null && attributes.size() != expectedSize)) {
            throw new DocumentStorageUnavailableException();
        }
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }

    private void forceDirectory() throws IOException {
        try (FileChannel channel = FileChannel.open(root, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException | SecurityException ignored) {
            // A failed database commit may leave only this unpublished orphan.
        }
    }
}

final class DocumentStorageUnavailableException extends RuntimeException {}

final class DocumentTooLargeException extends RuntimeException {}
