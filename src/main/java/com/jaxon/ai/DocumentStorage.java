package com.jaxon.ai;

import java.io.InputStream;

interface DocumentStorage {

    StoredDocument store(InputStream content);

    byte[] readVerified(String storageKey, long expectedSize, String expectedSha256);

    void delete(String storageKey);

    record StoredDocument(String storageKey, long sizeBytes, String sha256) {}
}
