package com.jaxon.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemDocumentStorageTest {

    @TempDir Path root;

    @Test
    void storesWithAnOpaqueKeyPrivatePermissionsAndVerifiedMetadata() throws Exception {
        byte[] content = "Fuente documental segura\n".getBytes();
        FileSystemDocumentStorage storage = new FileSystemDocumentStorage(root.toString());

        DocumentStorage.StoredDocument stored = storage.store(new ByteArrayInputStream(content));

        Path target = root.resolve(stored.storageKey());
        assertThat(stored.storageKey())
                .matches("[0-9a-f-]{36}\\.txt")
                .doesNotContain("Fuente", "documental");
        assertThat(stored.sizeBytes()).isEqualTo(content.length);
        assertThat(stored.sha256()).hasSize(64);
        assertThat(Files.readAllBytes(target)).isEqualTo(content);
        assertThat(Files.getPosixFilePermissions(root))
                .isEqualTo(PosixFilePermissions.fromString("rwx------"));
        assertThat(Files.getPosixFilePermissions(target))
                .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        assertThat(Files.list(root).toList()).containsExactly(target);

        storage.delete(stored.storageKey());
        assertThat(target).doesNotExist();
    }

    @Test
    void refusesOversizedContentAndLeavesNoPartialOrFinalFile() {
        byte[] content = new byte[(int) FileSystemDocumentStorage.MAX_BYTES + 1];
        FileSystemDocumentStorage storage = new FileSystemDocumentStorage(root.toString());

        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream(content)))
                .isInstanceOf(DocumentTooLargeException.class);
        assertThat(root.toFile().list()).isEmpty();
    }

    @Test
    void refusesAStorageKeyCollisionWithoutOverwritingExistingBytes() throws Exception {
        UUID storageId = UUID.fromString("4bd77e1f-e278-43cf-a458-d250795ee529");
        UUID temporaryId = UUID.fromString("14e2c1a8-f554-45fd-85e6-6c813d340440");
        List<UUID> ids = List.of(storageId, temporaryId);
        AtomicInteger nextId = new AtomicInteger();
        Path existing = root.resolve(storageId + ".txt");
        byte[] existingBytes = "existing".getBytes();
        Files.write(existing, existingBytes);
        FileSystemDocumentStorage storage =
                new FileSystemDocumentStorage(root.toString(), () -> ids.get(nextId.getAndIncrement()));

        assertThatThrownBy(() -> storage.store(new ByteArrayInputStream("replacement".getBytes())))
                .isInstanceOf(DocumentStorageUnavailableException.class);
        assertThat(Files.readAllBytes(existing)).isEqualTo(existingBytes);
        assertThat(Files.list(root).toList()).containsExactly(existing);
    }

    @Test
    void rejectsMissingSymlinkedAndEscapingStoragePaths() throws Exception {
        assertThatThrownBy(() -> new FileSystemDocumentStorage("")
                        .store(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(DocumentStorageUnavailableException.class);

        Path real = Files.createDirectory(root.resolve("real"));
        Path linked = root.resolve("linked");
        Files.createSymbolicLink(linked, real);
        FileSystemDocumentStorage linkedStorage = new FileSystemDocumentStorage(linked.toString());
        assertThatThrownBy(() -> linkedStorage.store(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(DocumentStorageUnavailableException.class);

        FileSystemDocumentStorage storage = new FileSystemDocumentStorage(real.toString());
        assertThatThrownBy(() -> storage.delete("../outside.txt"))
                .isInstanceOf(DocumentStorageUnavailableException.class);
    }
}
