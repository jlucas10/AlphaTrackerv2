package com.alphatracker.api.storage;

// What a StorageService hands back after a successful store(). storageKey is the
// only piece that gets persisted (on TradeAttachment) - sizeBytes/contentType are
// returned for the caller to validate or display immediately, not for storage.
public record StoredFile(String storageKey, long sizeBytes, String contentType) {
}
