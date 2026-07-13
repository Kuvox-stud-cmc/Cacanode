package com.cacanode.api.document.storage;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.cacanode.api.common.exception.custom.InternalServerErrorException;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@RequiredArgsConstructor
public class SeaweedFsDocumentStorage implements DocumentStorage {

    private final S3Client seaweedFsS3Client;
    private final SeaweedFsProperties properties;
    private final AtomicBoolean bucketReady = new AtomicBoolean(false);

    @Override
    public void store(String key, MultipartFile file) {
        try {
            ensureBucketExists();

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .build();

            seaweedFsS3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            throw new InternalServerErrorException("Unable to read uploaded document");
        } catch (RuntimeException e) {
            throw new InternalServerErrorException("Unable to store uploaded document", e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            seaweedFsS3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (RuntimeException e) {
            throw new InternalServerErrorException("Unable to delete stored document", e);
        }
    }

    private void ensureBucketExists() {
        if (bucketReady.get()) {
            return;
        }

        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }

            try {
                seaweedFsS3Client.headBucket(HeadBucketRequest.builder()
                        .bucket(properties.bucket())
                        .build());
            } catch (S3Exception e) {
                if (e.statusCode() != 404) {
                    throw e;
                }
                seaweedFsS3Client.createBucket(CreateBucketRequest.builder()
                        .bucket(properties.bucket())
                        .build());
            }

            bucketReady.set(true);
        }
    }
}
