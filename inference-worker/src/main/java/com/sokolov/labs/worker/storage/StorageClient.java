package com.sokolov.labs.worker.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Component
public class StorageClient {

    private final MinioClient client;
    private final MinioProperties props;

    public StorageClient(MinioClient client, MinioProperties props) {
        this.client = client;
        this.props = props;
    }

    public byte[] download(String key) throws IOException {
        try (InputStream in = client.getObject(GetObjectArgs.builder()
                .bucket(props.getBucket()).object(key).build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IOException("Failed to download " + key, e);
        }
    }

    public String upload(String key, byte[] payload, String contentType) {
        try (InputStream in = new ByteArrayInputStream(payload)) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(key)
                    .stream(in, payload.length, -1)
                    .contentType(contentType)
                    .build());
            return key;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload " + key, e);
        }
    }
}
