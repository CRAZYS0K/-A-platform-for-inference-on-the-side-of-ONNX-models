package com.sokolov.labs.worker.messaging;

import com.sokolov.labs.shared.dto.InferenceStatusMessage;
import com.sokolov.labs.shared.dto.TaskStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class StatusPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public StatusPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(UUID taskId, UUID ownerId, TaskStatus status, int progressPct,
                        String message, String resultS3Key, Double accuracy) {
        InferenceStatusMessage payload = new InferenceStatusMessage(
                taskId, ownerId, status, progressPct, message, resultS3Key, accuracy, Instant.now());
        kafkaTemplate.send(KafkaTopics.INFERENCE_STATUS, taskId.toString(), payload);
    }
}
