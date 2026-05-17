package com.sokolov.labs.backend.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Bean
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    @Bean
    public ApplicationRunner minioBucketInitializer(MinioClient client, MinioProperties properties) {
        return args -> {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
            if (!exists) {
                log.info("Creating MinIO bucket {}", properties.getBucket());
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            }
        };
    }
}
