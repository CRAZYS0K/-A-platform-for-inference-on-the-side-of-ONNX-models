package com.sokolov.labs.backend;

import com.sokolov.labs.backend.domain.Dataset;
import com.sokolov.labs.backend.domain.DatasetRepository;
import com.sokolov.labs.backend.domain.InferenceTaskRepository;
import com.sokolov.labs.backend.domain.Model;
import com.sokolov.labs.backend.domain.ModelRepository;
import com.sokolov.labs.backend.domain.UserAccount;
import com.sokolov.labs.backend.domain.UserAccountRepository;
import com.sokolov.labs.backend.messaging.KafkaTopics;
import com.sokolov.labs.shared.dto.InferenceStatusMessage;
import com.sokolov.labs.shared.dto.InferenceTaskMessage;
import com.sokolov.labs.shared.dto.TaskStatus;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

@EnabledIf(value = "com.sokolov.labs.backend.IntegrationTestBase#dockerAvailable",
        disabledReason = "Docker is not available — skipping Testcontainers integration test")
class TaskApiIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @Autowired UserAccountRepository userRepo;
    @Autowired ModelRepository modelRepo;
    @Autowired DatasetRepository datasetRepo;
    @Autowired InferenceTaskRepository taskRepo;
    @Autowired KafkaTemplate<String, Object> kafkaTemplate;

    private final RestTemplate rest = new RestTemplate();

    @BeforeEach
    void clean() {
        taskRepo.deleteAll();
        datasetRepo.deleteAll();
        modelRepo.deleteAll();
    }

    @Test
    void postTaskPublishesMessageAndStatusUpdateIsApplied() {
        String token = obtainAccessToken();

        UserAccount user = userRepo.findByKcSubject(jwtSubject(token))
                .orElseGet(() -> userRepo.save(new UserAccount(
                        UUID.randomUUID(), jwtSubject(token), "alice@example.com", "alice", Instant.now())));

        Model model = modelRepo.save(new Model(UUID.randomUUID(), user.getId(),
                "test-model", "models/" + user.getId() + "/m.onnx", 100,
                "input", "output", "[1,1]", "[1,1]", Instant.now()));
        Dataset dataset = datasetRepo.save(new Dataset(UUID.randomUUID(), user.getId(),
                "test-dataset", Dataset.Kind.LABELED,
                "datasets/" + user.getId() + "/d.zip", 200, 5, Instant.now()));

        Map<String, Object> request = Map.of("modelId", model.getId(), "datasetId", dataset.getId());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                "http://localhost:" + port + "/api/tasks",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                new org.springframework.core.ParameterizedTypeReference<>() {});

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID taskId = UUID.fromString((String) resp.getBody().get("id"));
        assertThat(resp.getBody().get("status")).isEqualTo("PENDING");

        InferenceTaskMessage published = consumeOne(KafkaTopics.INFERENCE_TASKS,
                InferenceTaskMessage.class, "test-tasks-consumer");
        assertThat(published.taskId()).isEqualTo(taskId);
        assertThat(published.modelId()).isEqualTo(model.getId());
        assertThat(published.datasetId()).isEqualTo(dataset.getId());
        assertThat(published.labeled()).isTrue();

        kafkaTemplate.send(KafkaTopics.INFERENCE_STATUS, taskId.toString(),
                new InferenceStatusMessage(taskId, user.getId(), TaskStatus.SUCCEEDED,
                        100, "done", "results/key", 0.95, Instant.now()));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var refreshed = taskRepo.findById(taskId).orElseThrow();
            assertThat(refreshed.getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
            assertThat(refreshed.getProgressPct()).isEqualTo(100);
            assertThat(refreshed.getAccuracy()).isEqualByComparingTo(new BigDecimal("0.9500"));
            assertThat(refreshed.getResultS3Key()).isEqualTo("results/key");
            assertThat(refreshed.getFinishedAt()).isNotNull();
        });
    }

    private String jwtSubject(String token) {
        String payloadJson = new String(java.util.Base64.getUrlDecoder()
                .decode(token.split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
        com.fasterxml.jackson.databind.JsonNode node;
        try {
            node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payloadJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return node.get("sub").asText();
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
            ConsumerRecord<String, T> first = records.iterator().next();
            return first.value();
        }
    }

    private String obtainAccessToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "test-client");
        form.add("username", "alice");
        form.add("password", "secret");
        form.add("scope", "openid profile email");
        ResponseEntity<Map<String, Object>> resp = rest.exchange(
                issuerUri() + "/protocol/openid-connect/token",
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                new org.springframework.core.ParameterizedTypeReference<>() {});
        return (String) resp.getBody().get("access_token");
    }
}
