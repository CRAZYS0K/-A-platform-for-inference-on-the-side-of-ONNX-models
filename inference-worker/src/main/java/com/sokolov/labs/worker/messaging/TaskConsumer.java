package com.sokolov.labs.worker.messaging;

import com.sokolov.labs.shared.dto.InferenceTaskMessage;
import com.sokolov.labs.shared.dto.TaskStatus;
import com.sokolov.labs.worker.inference.InferenceRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(TaskConsumer.class);

    private final InferenceRunner runner;
    private final StatusPublisher publisher;

    public TaskConsumer(InferenceRunner runner, StatusPublisher publisher) {
        this.runner = runner;
        this.publisher = publisher;
    }

    @KafkaListener(topics = KafkaTopics.INFERENCE_TASKS,
            groupId = "${spring.kafka.consumer.group-id:onnxi-worker}",
            containerFactory = "kafkaListenerContainerFactory")
    public void onTask(InferenceTaskMessage task) throws Exception {
        log.info("Received task {} model={} dataset={}", task.taskId(), task.modelId(), task.datasetId());
        try {
            runner.run(task);
        } catch (Throwable t) {
            log.error("Inference failed for task {}: {}", task.taskId(), t.toString(), t);
            publisher.publish(task.taskId(), task.ownerId(), TaskStatus.FAILED, 100,
                    t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage(),
                    null, null);
            throw t;
        }
    }
}
