package com.sokolov.labs.worker.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

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
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate,
                                                 @Value("${worker.retry.initial-interval-ms:2000}") long initial,
                                                 @Value("${worker.retry.max-interval-ms:30000}") long max,
                                                 @Value("${worker.retry.max-elapsed-ms:120000}") long maxElapsed) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(KafkaTopics.INFERENCE_TASKS_DLQ, -1));
        ExponentialBackOff backOff = new ExponentialBackOff(initial, 2.0);
        backOff.setMaxInterval(max);
        backOff.setMaxElapsedTime(maxElapsed);
        return new DefaultErrorHandler(recoverer, backOff);
    }
}
