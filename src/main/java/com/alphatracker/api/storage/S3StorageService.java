package com.alphatracker.api.storage;

import java.io.InputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

// S3 StorageService adapter - same key scheme and filename-sanitization
// guarantees as LocalFileStorageService (see StorageKeys), just against a
// bucket instead of a local directory. This is what makes screenshots
// survive a container restart/redeploy, which local disk cannot (Sprint 3.75
// deploy - see CONTEXT.md).
//
// Bucket access is scoped by IAM policy on the AWS side (the app's IAM user
// can only PutObject/GetObject/DeleteObject on this one bucket) - this class
// doesn't need to defend against a key escaping "its root" the way the local
// adapter does, since there's no shared filesystem to escape into.
@Service
@ConditionalOnProperty(prefix = "application.storage", name = "provider", havingValue = "s3")
public class S3StorageService implements StorageService {

    @Value("${application.storage.s3.bucket}")
    private String bucket;

    @Value("${application.storage.s3.region}")
    private String region;

    @Value("${application.storage.s3.access-key-id}")
    private String accessKeyId;

    @Value("${application.storage.s3.secret-access-key}")
    private String secretAccessKey;

    private S3Client s3Client;

    @PostConstruct
    void init() {
        this.s3Client = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .build();
    }

    @Override
    public StoredFile store(InputStream content, long sizeBytes, String originalFilename, String contentType, Long ownerId) {
        String storageKey = StorageKeys.generate(ownerId, originalFilename);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();

        try {
            s3Client.putObject(request, RequestBody.fromInputStream(content, sizeBytes));
        } catch (S3Exception e) {
            throw new StorageException("Failed to store file for owner " + ownerId, e);
        }

        return new StoredFile(storageKey, sizeBytes, contentType);
    }

    @Override
    public InputStream retrieve(String storageKey) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build();

        try {
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
            return response; // ResponseInputStream extends FilterInputStream - it IS an InputStream.
        } catch (NoSuchKeyException e) {
            throw new StorageException("No file found for storage key: " + storageKey, e);
        } catch (S3Exception e) {
            throw new StorageException("Failed to read file for storage key: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build();

        try {
            // S3 DeleteObject is idempotent by design - deleting a key that
            // doesn't exist succeeds silently, which already matches the
            // interface's "no-op if already gone" contract with no extra code.
            s3Client.deleteObject(request);
        } catch (S3Exception e) {
            throw new StorageException("Failed to delete file for storage key: " + storageKey, e);
        }
    }
}
