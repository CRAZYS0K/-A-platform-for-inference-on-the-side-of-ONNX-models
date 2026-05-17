package com.sokolov.labs.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "models")
public class Model {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Column(name = "s3_key", nullable = false)
    private String s3Key;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "input_name")
    private String inputName;

    @Column(name = "output_name")
    private String outputName;

    @Column(name = "input_shape")
    private String inputShape;

    @Column(name = "output_shape")
    private String outputShape;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected Model() {
    }

    public Model(UUID id, UUID ownerId, String name, String s3Key, long sizeBytes,
                 String inputName, String outputName, String inputShape, String outputShape,
                 Instant uploadedAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.name = name;
        this.s3Key = s3Key;
        this.sizeBytes = sizeBytes;
        this.inputName = inputName;
        this.outputName = outputName;
        this.inputShape = inputShape;
        this.outputShape = outputShape;
        this.uploadedAt = uploadedAt;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public String getS3Key() { return s3Key; }
    public long getSizeBytes() { return sizeBytes; }
    public String getInputName() { return inputName; }
    public String getOutputName() { return outputName; }
    public String getInputShape() { return inputShape; }
    public String getOutputShape() { return outputShape; }
    public Instant getUploadedAt() { return uploadedAt; }
}
