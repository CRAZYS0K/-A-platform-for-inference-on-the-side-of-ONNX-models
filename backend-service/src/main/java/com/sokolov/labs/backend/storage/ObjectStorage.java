package com.sokolov.labs.backend.storage;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Component
public class ObjectStorage {

    private final MinioClient client;
    private final MinioProperties properties;

    public ObjectStorage(MinioClient client, MinioProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public void upload(String key, InputStream stream, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .stream(stream, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to upload " + key, e);
        }
    }

    public InputStream download(String key) throws IOException {
        try {
            return client.getObject(GetObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to download " + key, e);
        }
    }

    public void delete(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to delete " + key, e);
        }
    }

    public String presignedGetUrl(String key) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(properties.getBucket())
                    .object(key)
                    .method(Method.GET)
                    .expiry((int) properties.getPresignedTtlSeconds(), TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to presign " + key, e);
        }
    }

    public static class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
