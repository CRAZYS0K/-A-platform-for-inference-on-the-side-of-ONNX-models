package com.sokolov.labs.backend.messaging;

import com.sokolov.labs.backend.service.TaskService;
import com.sokolov.labs.shared.dto.InferenceStatusMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TaskStatusListener {

    private final TaskService taskService;

    public TaskStatusListener(TaskService taskService) {
        this.taskService = taskService;
    }

    @KafkaListener(topics = KafkaTopics.INFERENCE_STATUS, groupId = "${spring.kafka.consumer.group-id:onnxi-backend}")
    public void onStatusUpdate(InferenceStatusMessage message) {
        taskService.applyStatus(message);
    }
}
