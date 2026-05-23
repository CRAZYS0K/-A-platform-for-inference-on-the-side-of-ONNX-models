package com.sokolov.labs.worker.messaging;

public final class KafkaTopics {

    public static final String INFERENCE_TASKS = "inference.tasks";
    public static final String INFERENCE_STATUS = "inference.status";
    public static final String INFERENCE_TASKS_DLQ = "inference.tasks.DLQ";
    public static final String INFERENCE_TASKS_CANCEL = "inference.tasks.cancel";

    private KafkaTopics() {
    }
}
