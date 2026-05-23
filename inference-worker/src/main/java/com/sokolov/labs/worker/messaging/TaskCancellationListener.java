package com.sokolov.labs.worker.messaging;

import com.sokolov.labs.shared.dto.CancelTaskMessage;
import com.sokolov.labs.worker.inference.CancellationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.stereotype.Component;

@Component
public class TaskCancellationListener {

    private static final Logger log = LoggerFactory.getLogger(TaskCancellationListener.class);

    private final CancellationRegistry registry;

    public TaskCancellationListener(CancellationRegistry registry) {
        this.registry = registry;
    }

    @KafkaListener(
            topics = KafkaTopics.INFERENCE_TASKS_CANCEL,
            groupId = "${spring.application.name:onnxi-worker}-cancel-${random.uuid}",
            properties = {
                    JsonDeserializer.VALUE_DEFAULT_TYPE + "=com.sokolov.labs.shared.dto.CancelTaskMessage",
                    JsonDeserializer.USE_TYPE_INFO_HEADERS + "=false"
            })
    public void onCancel(CancelTaskMessage message) {
        registry.markCancelled(message.taskId());
        log.info("Received cancel request for task {}", message.taskId());
    }
}
