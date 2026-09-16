package com.alphatracker.api.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

// Local filesystem StorageService adapter. Every key it hands out is
// {ownerId}/{uuid}{extension} - never the caller's original filename, so a
// malicious filename (e.g. "../../etc/passwd") can never influence where a
// file lands on disk. See StorageService for the interface contract this
// fulfills; an S3 adapter will implement the same interface for Sprint 5.
@Service
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
        String storageKey = ownerId + "/" + UUID.randomUUID() + extractExtension(originalFilename);
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

    // Keeps only a short, whitelisted extension from the caller-supplied
    // filename. Everything else about the original filename is discarded -
    // the on-disk name is always a fresh UUID, so this is purely cosmetic
    // (keeps ".png"/".jpg" etc. on the file) and never trusted for pathing.
    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        String ext = originalFilename.substring(dot + 1);
        if (ext.length() > 10 || !ext.chars().allMatch(Character::isLetterOrDigit)) {
            return "";
        }
        return "." + ext.toLowerCase();
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
