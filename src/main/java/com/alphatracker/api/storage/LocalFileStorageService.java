package com.alphatracker.api.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

// Local filesystem StorageService adapter. Every key it hands out is
// {ownerId}/{uuid}{extension} - never the caller's original filename, so a
// malicious filename (e.g. "../../etc/passwd") can never influence where a
// file lands on disk. See StorageService for the interface contract this
// fulfills; S3StorageService implements the same interface for production.
//
// matchIfMissing=true: local dev needs zero config to keep working exactly as
// before. Production sets STORAGE_PROVIDER=s3, which both disables this bean
// and activates S3StorageService - only one StorageService implementation is
// ever active at a time, so nothing has to choose between them at call sites.
@Service
@ConditionalOnProperty(prefix = "application.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements StorageService {

    @Value("${application.storage.local.base-path}")
    private String basePathConfig;

    private Path basePath;

    @PostConstruct
    void init() {
        this.basePath = Path.of(basePathConfig).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create storage base path: " + basePath, e);
        }
    }

    @Override
    public StoredFile store(InputStream content, long sizeBytes, String originalFilename, String contentType, Long ownerId) {
        String storageKey = StorageKeys.generate(ownerId, originalFilename);
        Path target = resolveWithinBase(storageKey);

        try {
            Files.createDirectories(target.getParent());
            Files.copy(content, target);
        } catch (IOException e) {
            throw new StorageException("Failed to store file for owner " + ownerId, e);
        }

        return new StoredFile(storageKey, sizeBytes, contentType);
    }

    @Override
    public InputStream retrieve(String storageKey) {
        Path target = resolveWithinBase(storageKey);
        if (!Files.exists(target)) {
            throw new StorageException("No file found for storage key: " + storageKey);
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new StorageException("Failed to read file for storage key: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveWithinBase(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new StorageException("Failed to delete file for storage key: " + storageKey, e);
        }
    }

    // Defense in depth: even though this adapter generates every storageKey
    // itself, retrieve()/delete() take a key that round-tripped through the
    // database. Normalizing before the startsWith check is what actually
    // catches a "../" escape - without normalize(), resolve() alone leaves
    // the ".." segments in place and the startsWith check would pass.
    private Path resolveWithinBase(String storageKey) {
        Path resolved = basePath.resolve(storageKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new StorageException("Storage key escapes storage root: " + storageKey);
        }
        return resolved;
    }
}
