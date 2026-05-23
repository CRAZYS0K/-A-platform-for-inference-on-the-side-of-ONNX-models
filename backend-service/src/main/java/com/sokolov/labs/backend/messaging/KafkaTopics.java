package com.sokolov.labs.backend.messaging;

public final class KafkaTopics {

    public static final String INFERENCE_TASKS = "inference.tasks";
    public static final String INFERENCE_STATUS = "inference.status";
    public static final String INFERENCE_TASKS_DLQ = "inference.tasks.DLQ";
    public static final String INFERENCE_TASKS_CANCEL = "inference.tasks.cancel";
    public static final String INFERENCE_NOTIFICATIONS = "inference.notifications";

    private KafkaTopics() {
    }
}
