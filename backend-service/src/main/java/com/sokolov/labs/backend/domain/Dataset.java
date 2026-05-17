package com.sokolov.labs.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "datasets")
public class Dataset {

    public enum Kind { LABELED, UNLABELED }

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "file_count")
    private Integer fileCount;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected Dataset() {
    }

    public Dataset(UUID id, UUID ownerId, String name, Kind kind, String s3Key,
                   long sizeBytes, Integer fileCount, Instant uploadedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.kind = kind;
        this.s3Key = s3Key;
        this.sizeBytes = sizeBytes;
        this.fileCount = fileCount;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public Kind getKind() { return kind; }
    public String getS3Key() { return s3Key; }
    public long getSizeBytes() { return sizeBytes; }
    public Integer getFileCount() { return fileCount; }
    public Instant getUploadedAt() { return uploadedAt; }
}
