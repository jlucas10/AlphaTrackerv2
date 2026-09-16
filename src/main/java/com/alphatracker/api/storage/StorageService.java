package com.alphatracker.api.storage;

import java.io.InputStream;

/**
 * Abstraction over "where trade attachment bytes physically live." TradeAttachment
 * rows never know whether a file sits on local disk or in S3 — they only hold the
 * opaque storageKey this interface hands back from store(). Swapping the local
 * filesystem adapter for an S3 adapter (Sprint 5 deploy) means adding a new
 * implementation of this interface, not touching any caller of it.
 */
public interface StorageService {

    // Persists the given bytes under a key scoped to ownerId and returns the
    // stored file's metadata, including the storageKey callers must save
    // (on TradeAttachment) to retrieve or delete it later.
    StoredFile store(InputStream content, long sizeBytes, String originalFilename, String contentType, Long ownerId);

    // Opens the bytes previously stored under storageKey. Throws StorageException
    // if the key doesn't resolve to a file this adapter manages.
    InputStream retrieve(String storageKey);

    // Deletes the file at storageKey. No-op if it's already gone, so callers
    // don't need to check existence first.
    void delete(String storageKey);
}
