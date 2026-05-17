package com.sokolov.labs.backend.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic inferenceTasksTopic() {
        return TopicBuilder.name(KafkaTopics.INFERENCE_TASKS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inferenceStatusTopic() {
        return TopicBuilder.name(KafkaTopics.INFERENCE_STATUS).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inferenceTasksDlqTopic() {
        return TopicBuilder.name(KafkaTopics.INFERENCE_TASKS_DLQ).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic inferenceNotificationsTopic() {
        return TopicBuilder.name(KafkaTopics.INFERENCE_NOTIFICATIONS).partitions(1).replicas(1).build();
    }

    @Bean
    public JsonDeserializer<Object> kafkaJsonDeserializer() {
        JsonDeserializer<Object> deserializer = new JsonDeserializer<>();
        deserializer.addTrustedPackages("com.sokolov.labs.shared.dto");
        return deserializer;
    }
}
