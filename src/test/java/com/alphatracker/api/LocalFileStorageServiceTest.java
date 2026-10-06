package com.alphatracker.api;

import com.alphatracker.api.storage.LocalFileStorageService;
import com.alphatracker.api.storage.StorageException;
import com.alphatracker.api.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class LocalFileStorageServiceTest {

    private LocalFileStorageService storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageService = new LocalFileStorageService();
        // basePathConfig and basePath are populated by Spring (@Value +
        // @PostConstruct) in production; wire them by hand here since this
        // is a plain unit test with no Spring context.
        ReflectionTestUtils.setField(storageService, "basePathConfig", tempDir.toString());
        ReflectionTestUtils.invokeMethod(storageService, "init");
    }

    @Test
    @DisplayName("retrieve() returns exactly the bytes that were stored")
    void retrieveReturnsStoredBytes() throws IOException {
        byte[] payload = "round-trip-me".getBytes(StandardCharsets.UTF_8);
        StoredFile stored = storageService.store(new ByteArrayInputStream(payload), payload.length, "note.txt", "text/plain", 7L);

        byte[] readBack;
        try (InputStream in = storageService.retrieve(stored.storageKey())) {
            readBack = in.readAllBytes();
        }

        assertArrayEquals(payload, readBack);
    }

    @Test
    @DisplayName("retrieve() rejects a storage key that attempts to escape the base path")
    void retrieveRejectsPathTraversal() {
        assertThrows(StorageException.class,
                () -> storageService.retrieve("../../../../etc/passwd"));
    }

    @Test
    @DisplayName("a hostile original filename never influences where the file lands on disk")
    void maliciousFilenameCannotEscapeOwnerDirectory() {
        byte[] payload = "safe".getBytes(StandardCharsets.UTF_8);
        StoredFile stored = storageService.store(new ByteArrayInputStream(payload), payload.length,
                "../../../etc/passwd", "text/plain", 9L);

        // The malicious path segments are discarded entirely - only a UUID
        // under the owner's own directory is ever used, and no unexpected
        // extension is carried over from a filename like this.
        assertTrue(stored.storageKey().startsWith("9/"));
        assertFalse(stored.storageKey().contains(".."));
        assertFalse(stored.storageKey().contains("/etc/"));
    }
}
