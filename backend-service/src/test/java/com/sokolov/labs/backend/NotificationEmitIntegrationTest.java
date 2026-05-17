package com.sokolov.labs.backend;

import com.sokolov.labs.backend.domain.Dataset;
import com.sokolov.labs.backend.domain.DatasetRepository;
import com.sokolov.labs.backend.domain.InferenceTask;
import com.sokolov.labs.backend.domain.InferenceTaskRepository;
import com.sokolov.labs.backend.domain.Model;
import com.sokolov.labs.backend.domain.ModelRepository;
import com.sokolov.labs.backend.domain.NotificationPreferences;
import com.sokolov.labs.backend.domain.NotificationPreferencesRepository;
import com.sokolov.labs.backend.domain.UserAccount;
import com.sokolov.labs.backend.domain.UserAccountRepository;
import com.sokolov.labs.backend.messaging.KafkaTopics;
import com.sokolov.labs.shared.dto.InferenceStatusMessage;
import com.sokolov.labs.shared.dto.NotificationEvent;
import com.sokolov.labs.shared.dto.TaskStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf(value = "com.sokolov.labs.backend.IntegrationTestBase#dockerAvailable",
        disabledReason = "Docker is not available")
class NotificationEmitIntegrationTest extends IntegrationTestBase {

    @Autowired UserAccountRepository userRepo;
    @Autowired NotificationPreferencesRepository prefsRepo;
    @Autowired ModelRepository modelRepo;
    @Autowired DatasetRepository datasetRepo;
    @Autowired InferenceTaskRepository taskRepo;
    @Autowired KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void successStatusEmitsNotificationWithUserEmail() {
        UserAccount user = userRepo.save(new UserAccount(
                UUID.randomUUID(), "kc-" + UUID.randomUUID(),
                "ivan@example.com", "ivan", Instant.now()));

        NotificationPreferences prefs = new NotificationPreferences(user.getId());
        prefs.setEmailEnabled(true);
        prefs.setTelegramEnabled(true);
        prefs.setTelegramChatId("999");
        prefsRepo.save(prefs);

        Model model = modelRepo.save(new Model(UUID.randomUUID(), user.getId(),
                "m", "models/m.onnx", 1, null, null, null, null, Instant.now()));
        Dataset dataset = datasetRepo.save(new Dataset(UUID.randomUUID(), user.getId(),
                "d", Dataset.Kind.LABELED, "datasets/d.zip", 1, 1, Instant.now()));
        InferenceTask task = taskRepo.save(new InferenceTask(UUID.randomUUID(), user.getId(),
                model.getId(), dataset.getId(), TaskStatus.PENDING, Instant.now()));
        UUID taskId = task.getId();

        kafkaTemplate.send(KafkaTopics.INFERENCE_STATUS, taskId.toString(),
                new InferenceStatusMessage(taskId, user.getId(), TaskStatus.SUCCEEDED,
                        100, "ok", "results/foo", 0.88, Instant.now()));

        NotificationEvent evt = consumeOne(KafkaTopics.INFERENCE_NOTIFICATIONS,
                NotificationEvent.class, "test-notif-" + UUID.randomUUID());
        assertThat(evt.taskId()).isEqualTo(taskId);
        assertThat(evt.email()).isEqualTo("ivan@example.com");
        assertThat(evt.emailEnabled()).isTrue();
        assertThat(evt.telegramEnabled()).isTrue();
        assertThat(evt.telegramChatId()).isEqualTo("999");
        assertThat(evt.accuracy()).isEqualTo(0.88);
        assertThat(evt.status()).isEqualTo(TaskStatus.SUCCEEDED);
    }

    private <T> T consumeOne(String topic, Class<T> type, String groupId) {
        Map<String, Object> props = new HashMap<>(KafkaTestUtils.consumerProps(
                KAFKA.getBootstrapServers(), groupId, "true"));
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.sokolov.labs.shared.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, type.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        try (var consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<String, T>(props)) {
            consumer.subscribe(java.util.List.of(topic));
            var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15), 1);
            assertThat(records.count()).isGreaterThan(0);
            return records.iterator().next().value();
        }
    }
}
