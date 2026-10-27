package com.jaxon.discovery;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
class DiscoveryResultFileReader {

    Path claim(Path root, String mailboxName, String fileName, String claimName) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path mailbox = singleName(mailboxName);
        Path source = singleName(fileName);
        Path target = singleName(claimName);
        Path claimsPath = normalizedRoot.resolve(".jaxon-claims");

        try (RootStreams rootStreams = openRoot(normalizedRoot)) {
            SecureDirectoryStream<Path> secureRoot = rootStreams.root();
            createPrivateDirectory(claimsPath);
            try (SecureDirectoryStream<Path> sourceDirectory =
                            secureRoot.newDirectoryStream(mailbox, LinkOption.NOFOLLOW_LINKS);
                    SecureDirectoryStream<Path> claims = secureRoot.newDirectoryStream(
                            Path.of(".jaxon-claims"), LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes attributes = attributes(sourceDirectory, source);
                if (!attributes.isRegularFile()) throw new IOException("Unsafe result source");
                sourceDirectory.move(source, claims, target);
            }
        }
        return claimsPath.resolve(target);
    }

    BasicFileAttributes attributes(Path root, Path file) throws IOException {
        return withParent(root, file, this::attributes);
    }

    void delete(Path root, Path file) throws IOException {
        withParent(root, file, (directory, name) -> {
            directory.deleteFile(name);
            return null;
        });
    }

    boolean hasClaim(Path root, String mailboxName, String jobId) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        String prefix = singleName(mailboxName) + "." + jobId + ".";
        try (RootStreams rootStreams = openRoot(normalizedRoot);
                SecureDirectoryStream<Path> claims = rootStreams.root().newDirectoryStream(
                        Path.of(".jaxon-claims"), LinkOption.NOFOLLOW_LINKS)) {
            for (Path entry : claims) {
                String name = entry.getFileName().toString();
                if (name.startsWith(prefix) && name.endsWith(".json")
                        && attributes(claims, entry.getFileName()).isRegularFile()) {
                    return true;
                }
            }
            return false;
        } catch (NoSuchFileException exception) {
            return false;
        }
    }

    boolean hasFinal(Path root, String mailboxName, String fileName) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path mailbox = singleName(mailboxName);
        Path file = singleName(fileName);
        try (RootStreams rootStreams = openRoot(normalizedRoot);
                SecureDirectoryStream<Path> source = rootStreams.root().newDirectoryStream(
                        mailbox, LinkOption.NOFOLLOW_LINKS)) {
            return attributes(source, file).isRegularFile();
        } catch (NoSuchFileException exception) {
            return false;
        }
    }

    List<Path> claims(Path root, int limit) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<Path> result = new ArrayList<>();
        try (RootStreams rootStreams = openRoot(normalizedRoot);
                SecureDirectoryStream<Path> claims = rootStreams.root().newDirectoryStream(
                        Path.of(".jaxon-claims"), LinkOption.NOFOLLOW_LINKS)) {
            for (Path entry : claims) {
                Path name = entry.getFileName();
                if (attributes(claims, name).isRegularFile()) {
                    result.add(normalizedRoot.resolve(".jaxon-claims").resolve(name));
                }
                if (result.size() >= limit) break;
            }
            return result;
        } catch (NoSuchFileException exception) {
            return List.of();
        }
    }

    ReadResult read(Path root, Path file, Duration stabilityAge, Instant now) {
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path normalizedFile = file.toAbsolutePath().normalize();
            if (!normalizedFile.startsWith(normalizedRoot)
                    || normalizedFile.equals(normalizedRoot)
                    || Files.isSymbolicLink(normalizedRoot)
                    || !Files.isDirectory(normalizedRoot, LinkOption.NOFOLLOW_LINKS)) {
                throw new UnsafeResultFileException("FILE_UNSAFE");
            }
            Path relative = normalizedRoot.relativize(normalizedFile);
            try (RootStreams rootStreams = openRoot(normalizedRoot)) {
                return read(rootStreams.root(), relative, stabilityAge, now);
            }
        } catch (ResultNotStableException | UnsafeResultFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new UnsafeResultFileException("FILE_UNSAFE");
        }
    }

    private ReadResult read(
            SecureDirectoryStream<Path> root, Path relative, Duration stabilityAge, Instant now)
            throws IOException {
        List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
        SecureDirectoryStream<Path> directory = root;
        try {
            for (int index = 0; index < relative.getNameCount() - 1; index++) {
                directory = directory.newDirectoryStream(relative.getName(index), LinkOption.NOFOLLOW_LINKS);
                opened.add(directory);
            }
            Path fileName = relative.getFileName();
            BasicFileAttributes before = attributes(directory, fileName);
            if (!before.isRegularFile()) throw new UnsafeResultFileException("FILE_UNSAFE");
            if (before.size() > DiscoveryResultContract.MAX_RESULT_BYTES) {
                throw new UnsafeResultFileException("FILE_TOO_LARGE");
            }
            if (before.lastModifiedTime().toInstant().plus(stabilityAge).isAfter(now)) {
                throw new ResultNotStableException();
            }
            byte[] bytes;
            try (SeekableByteChannel channel = directory.newByteChannel(
                    fileName, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                ByteArrayOutputStream output = new ByteArrayOutputStream((int) before.size());
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                long total = 0;
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    total += buffer.remaining();
                    if (total > DiscoveryResultContract.MAX_RESULT_BYTES) {
                        throw new UnsafeResultFileException("FILE_TOO_LARGE");
                    }
                    output.write(buffer.array(), buffer.position(), buffer.remaining());
                    buffer.clear();
                }
                bytes = output.toByteArray();
            }
            BasicFileAttributes after = attributes(directory, fileName);
            if (!after.isRegularFile()
                    || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !java.util.Objects.equals(before.fileKey(), after.fileKey())) {
                throw new ResultNotStableException();
            }
            try {
                String json = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
                return new ReadResult(json, sha256(bytes), bytes.length);
            } catch (CharacterCodingException exception) {
                throw new UnsafeResultFileException("INVALID_UTF8");
            }
        } finally {
            Collections.reverse(opened);
            for (SecureDirectoryStream<Path> stream : opened) stream.close();
        }
    }

    private BasicFileAttributes attributes(SecureDirectoryStream<Path> directory, Path file)
            throws IOException {
        BasicFileAttributeView view = directory.getFileAttributeView(
                file, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        if (view == null) throw new IOException("Basic file attributes unavailable");
        return view.readAttributes();
    }

    private <T> T withParent(Path root, Path file, SecureFileOperation<T> operation)
            throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        if (!normalizedFile.startsWith(normalizedRoot) || normalizedFile.equals(normalizedRoot)) {
            throw new IOException("Path escapes root");
        }
        Path relative = normalizedRoot.relativize(normalizedFile);
        List<SecureDirectoryStream<Path>> opened = new ArrayList<>();
        try (RootStreams rootStreams = openRoot(normalizedRoot)) {
            SecureDirectoryStream<Path> directory = rootStreams.root();
            try {
                for (int index = 0; index < relative.getNameCount() - 1; index++) {
                    directory = directory.newDirectoryStream(
                            relative.getName(index), LinkOption.NOFOLLOW_LINKS);
                    opened.add(directory);
                }
                return operation.apply(directory, relative.getFileName());
            } finally {
                Collections.reverse(opened);
                for (SecureDirectoryStream<Path> openedDirectory : opened) openedDirectory.close();
            }
        }
    }

    private static Path singleName(String value) throws IOException {
        Path path = Path.of(value);
        if (path.getNameCount() != 1 || path.isAbsolute() || ".".equals(value) || "..".equals(value)) {
            throw new IOException("Invalid path component");
        }
        return path;
    }

    private static void createPrivateDirectory(Path directory) throws IOException {
        try {
            Files.createDirectory(directory,
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } catch (FileAlreadyExistsException ignored) {
            // The descriptor-relative open below verifies that the existing entry is a real directory.
        }
    }

    private static RootStreams openRoot(Path root) throws IOException {
        Path parentPath = root.getParent();
        Path rootName = root.getFileName();
        if (parentPath == null || rootName == null) throw new IOException("Unsafe result root");
        DirectoryStream<Path> parentStream = Files.newDirectoryStream(parentPath);
        if (!(parentStream instanceof SecureDirectoryStream<Path> secureParent)) {
            parentStream.close();
            throw new IOException("Secure directory operations unavailable");
        }
        try {
            SecureDirectoryStream<Path> secureRoot =
                    secureParent.newDirectoryStream(rootName, LinkOption.NOFOLLOW_LINKS);
            return new RootStreams(parentStream, secureRoot);
        } catch (IOException exception) {
            parentStream.close();
            throw exception;
        }
    }

    @FunctionalInterface
    private interface SecureFileOperation<T> {
        T apply(SecureDirectoryStream<Path> directory, Path name) throws IOException;
    }

    private record RootStreams(
            DirectoryStream<Path> parent, SecureDirectoryStream<Path> root) implements AutoCloseable {
        @Override
        public void close() throws IOException {
            try {
                root.close();
            } finally {
                parent.close();
            }
        }
    }

    static String metadataFingerprint(long size, Instant modifiedAt) {
        return sha256((size + ":" + modifiedAt).getBytes(StandardCharsets.UTF_8));
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
        UnsafeResultFileException(String reasonCode) { this.reasonCode = reasonCode; }
        String reasonCode() { return reasonCode; }
    }
}
