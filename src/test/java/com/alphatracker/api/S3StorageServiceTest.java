package com.alphatracker.api;

import com.alphatracker.api.storage.S3StorageService;
import com.alphatracker.api.storage.StorageException;
import com.alphatracker.api.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Bypasses @PostConstruct entirely (which would build a real S3Client against
// real AWS credentials) - the mocked S3Client is injected directly via
// reflection, same pattern as LocalFileStorageServiceTest wiring basePath by
// hand instead of going through Spring.
@ExtendWith(MockitoExtension.class)
public class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    private S3StorageService storageService;

    private static final String BUCKET = "alphatracker-test-bucket";

    @BeforeEach
    void setUp() {
        storageService = new S3StorageService();
        ReflectionTestUtils.setField(storageService, "bucket", BUCKET);
        ReflectionTestUtils.setField(storageService, "s3Client", s3Client);
    }

    @Test
    @DisplayName("store() puts the object under an owner-scoped key and returns its metadata")
    void storePutsObjectAndReturnsMetadata() {
        byte[] payload = "screenshot-bytes".getBytes();
        InputStream content = new ByteArrayInputStream(payload);

        StoredFile result = storageService.store(content, payload.length, "chart.png", "image/png", 42L);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client, times(1)).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest sentRequest = requestCaptor.getValue();
        assertEquals(BUCKET, sentRequest.bucket());
        assertEquals("image/png", sentRequest.contentType());
        assertEquals(payload.length, sentRequest.contentLength());

        assertTrue(result.storageKey().startsWith("42/"));
        assertTrue(result.storageKey().endsWith(".png"));
        assertEquals(payload.length, result.sizeBytes());
        assertEquals("image/png", result.contentType());
    }

    @Test
    @DisplayName("store() wraps an S3 failure in StorageException")
    void storeWrapsS3Failure() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        assertThrows(StorageException.class, () -> storageService.store(
                new ByteArrayInputStream("x".getBytes()), 1L, "a.png", "image/png", 1L));
    }

    @Test
    @DisplayName("retrieve() returns the object's bytes for the given key")
    void retrieveReturnsObjectStream() {
        ResponseInputStream<GetObjectResponse> response = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                software.amazon.awssdk.http.AbortableInputStream.create(new ByteArrayInputStream("bytes".getBytes())));
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(response);

        InputStream result = storageService.retrieve("1/uuid.png");

        assertNotNull(result);
        ArgumentCaptor<GetObjectRequest> requestCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client, times(1)).getObject(requestCaptor.capture());
        assertEquals(BUCKET, requestCaptor.getValue().bucket());
        assertEquals("1/uuid.png", requestCaptor.getValue().key());
    }

    @Test
    @DisplayName("retrieve() throws StorageException for a key that doesn't exist")
    void retrieveThrowsForMissingKey() {
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("not found").build());

        assertThrows(StorageException.class, () -> storageService.retrieve("1/missing.png"));
    }

    @Test
    @DisplayName("delete() calls DeleteObject with the given key")
    void deleteCallsDeleteObject() {
        storageService.delete("1/uuid.png");

        ArgumentCaptor<DeleteObjectRequest> requestCaptor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client, times(1)).deleteObject(requestCaptor.capture());
        assertEquals(BUCKET, requestCaptor.getValue().bucket());
        assertEquals("1/uuid.png", requestCaptor.getValue().key());
    }

    @Test
    @DisplayName("delete() wraps an S3 failure in StorageException")
    void deleteWrapsS3Failure() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("boom").build());

        assertThrows(StorageException.class, () -> storageService.delete("1/uuid.png"));
    }

    @Test
    @DisplayName("a hostile original filename never influences the generated key")
    void maliciousFilenameCannotEscapeOwnerPrefix() {
        byte[] payload = "safe".getBytes();

        StoredFile result = storageService.store(new ByteArrayInputStream(payload), payload.length,
                "../../../etc/passwd", "text/plain", 9L);

        assertTrue(result.storageKey().startsWith("9/"));
        assertFalse(result.storageKey().contains(".."));
        assertFalse(result.storageKey().contains("/etc/"));
    }
}
