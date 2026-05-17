package com.sokolov.labs.backend.service;

import com.sokolov.labs.backend.domain.Dataset;
import com.sokolov.labs.backend.domain.InferenceTask;
import com.sokolov.labs.backend.domain.InferenceTaskRepository;
import com.sokolov.labs.backend.domain.Model;
import com.sokolov.labs.backend.messaging.KafkaTopics;
import com.sokolov.labs.shared.dto.InferenceStatusMessage;
import com.sokolov.labs.shared.dto.InferenceTaskMessage;
import com.sokolov.labs.shared.dto.TaskStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final InferenceTaskRepository taskRepository;
    private final ModelService modelService;
    private final DatasetService datasetService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final NotificationEmitter notificationEmitter;
    private final MeterRegistry meterRegistry;

    public TaskService(InferenceTaskRepository taskRepository,
                       ModelService modelService,
                       DatasetService datasetService,
                       KafkaTemplate<String, Object> kafkaTemplate,
                       NotificationEmitter notificationEmitter,
                       MeterRegistry meterRegistry) {
        this.taskRepository = taskRepository;
        this.modelService = modelService;
        this.datasetService = datasetService;
        this.kafkaTemplate = kafkaTemplate;
        this.notificationEmitter = notificationEmitter;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public InferenceTask create(UUID ownerId, UUID modelId, UUID datasetId) {
        Model model = modelService.get(ownerId, modelId);
        Dataset dataset = datasetService.get(ownerId, datasetId);

        InferenceTask task = new InferenceTask(
                UUID.randomUUID(), ownerId, model.getId(), dataset.getId(),
                TaskStatus.PENDING, Instant.now());
        taskRepository.save(task);

        InferenceTaskMessage payload = new InferenceTaskMessage(
                task.getId(), ownerId, model.getId(), dataset.getId(),
                model.getS3Key(), dataset.getS3Key(),
                dataset.getKind() == Dataset.Kind.LABELED);
        kafkaTemplate.send(KafkaTopics.INFERENCE_TASKS, task.getId().toString(), payload);
        log.info("Published task {} (model={}, dataset={}) to Kafka", task.getId(), modelId, datasetId);
        Counter.builder("inference_tasks_total")
                .tag("status", TaskStatus.PENDING.name())
                .register(meterRegistry)
                .increment();
        return task;
    }

    @Transactional(readOnly = true)
    public Page<InferenceTask> list(UUID ownerId, Pageable pageable) {
        return taskRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable);
    }

    @Transactional(readOnly = true)
    public InferenceTask get(UUID ownerId, UUID id) {
        return taskRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new TaskNotFoundException(id));
    }

    @Transactional
    public void applyStatus(InferenceStatusMessage update) {
        taskRepository.findById(update.taskId()).ifPresentOrElse(task -> {
            TaskStatus prev = task.getStatus();
            task.setStatus(update.status());
            task.setProgressPct(Math.max(task.getProgressPct(), update.progressPct()));
            if (update.message() != null) {
                task.setErrorMessage(update.message());
            }
            if (update.resultS3Key() != null) {
                task.setResultS3Key(update.resultS3Key());
            }
            if (update.accuracy() != null) {
                task.setAccuracy(BigDecimal.valueOf(update.accuracy()));
            }
            if (update.status() == TaskStatus.RUNNING && task.getStartedAt() == null) {
                task.setStartedAt(Instant.now());
            }
            if (update.status() == TaskStatus.SUCCEEDED
                    || update.status() == TaskStatus.FAILED
                    || update.status() == TaskStatus.CANCELED) {
                task.setFinishedAt(Instant.now());
            }
            log.debug("Task {} status: {} -> {} (progress {}%)", task.getId(), prev, task.getStatus(), task.getProgressPct());
            if (prev != task.getStatus()) {
                Counter.builder("inference_tasks_total")
                        .tag("status", task.getStatus().name())
                        .register(meterRegistry)
                        .increment();
            }
            notificationEmitter.emitIfFinal(update);
        }, () -> log.warn("Status update for unknown task {}", update.taskId()));
    }

    public static class TaskNotFoundException extends RuntimeException {
        public TaskNotFoundException(UUID id) {
            super("Task not found: " + id);
        }
    }
}
