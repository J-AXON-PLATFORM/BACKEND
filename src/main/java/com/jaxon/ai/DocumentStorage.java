package com.jaxon.ai;

import java.io.InputStream;

interface DocumentStorage {

    StoredDocument store(InputStream content);

    void delete(String storageKey);

    record StoredDocument(String storageKey, long sizeBytes, String sha256) {}
}
