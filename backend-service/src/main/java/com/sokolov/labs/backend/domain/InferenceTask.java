package com.sokolov.labs.backend.domain;

import com.sokolov.labs.shared.dto.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inference_tasks")
public class InferenceTask {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "model_id", nullable = false)
    private UUID modelId;

    @Column(name = "dataset_id", nullable = false)
    private UUID datasetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status;

    @Column(name = "progress_pct", nullable = false)
    private int progressPct;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "result_s3_key")
    private String resultS3Key;

    @Column
    private BigDecimal accuracy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected InferenceTask() {
    }

    public InferenceTask(UUID id, UUID ownerId, UUID modelId, UUID datasetId,
                         TaskStatus status, Instant createdAt) {
        this.id = id;
        this.ownerId = ownerId;
        this.modelId = modelId;
        this.datasetId = datasetId;
        this.status = status;
        this.progressPct = 0;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getModelId() { return modelId; }
    public UUID getDatasetId() { return datasetId; }
    public TaskStatus getStatus() { return status; }
    public int getProgressPct() { return progressPct; }
    public String getErrorMessage() { return errorMessage; }
    public String getResultS3Key() { return resultS3Key; }
    public BigDecimal getAccuracy() { return accuracy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }

    public void setStatus(TaskStatus status) { this.status = status; }
    public void setProgressPct(int progressPct) { this.progressPct = progressPct; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setResultS3Key(String resultS3Key) { this.resultS3Key = resultS3Key; }
    public void setAccuracy(BigDecimal accuracy) { this.accuracy = accuracy; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
