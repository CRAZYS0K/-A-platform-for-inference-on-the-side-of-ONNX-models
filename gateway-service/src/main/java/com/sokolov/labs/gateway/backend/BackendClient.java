package com.sokolov.labs.gateway.backend;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class BackendClient {

    private static final Logger log = LoggerFactory.getLogger(BackendClient.class);
    private static final String CB_NAME = "backend";

    private final RestClient restClient;
    private final OAuth2AuthorizedClientManager authorizedClientManager;

    public BackendClient(@Value("${backend.base-url}") String baseUrl,
                         OAuth2AuthorizedClientManager authorizedClientManager) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null) {
                        request.getHeaders().add("X-Correlation-Id", correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
        this.authorizedClientManager = authorizedClientManager;
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackListModels")
    public List<ModelDto> listModels() {
        return get("/api/models?size=50", new ParameterizedTypeReference<Page<ModelDto>>() {}).content();
    }

    @SuppressWarnings("unused")
    private List<ModelDto> fallbackListModels(Throwable ex) {
        log.warn("Circuit breaker fallback for listModels: {}", ex.toString());
        return List.of();
    }

    @CircuitBreaker(name = CB_NAME)
    public ModelDto uploadModel(String name, MultipartFile file) throws IOException {
        return uploadMultipart("/api/models", "file", file, Map.of("name", name), ModelDto.class);
    }

    @CircuitBreaker(name = CB_NAME)
    public void deleteModel(UUID id) {
        deleteResource("/api/models/" + id);
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackListDatasets")
    public List<DatasetDto> listDatasets() {
        return get("/api/datasets?size=50", new ParameterizedTypeReference<Page<DatasetDto>>() {}).content();
    }

    @SuppressWarnings("unused")
    private List<DatasetDto> fallbackListDatasets(Throwable ex) {
        log.warn("Circuit breaker fallback for listDatasets: {}", ex.toString());
        return List.of();
    }

    @CircuitBreaker(name = CB_NAME)
    public DatasetDto uploadDataset(String name, String kind, MultipartFile file) throws IOException {
        return uploadMultipart("/api/datasets", "file", file,
                Map.of("name", name, "kind", kind == null ? "UNLABELED" : kind),
                DatasetDto.class);
    }

    @CircuitBreaker(name = CB_NAME)
    public void deleteDataset(UUID id) {
        deleteResource("/api/datasets/" + id);
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackListTasks")
    public List<TaskDto> listTasks() {
        return get("/api/tasks?size=50", new ParameterizedTypeReference<Page<TaskDto>>() {}).content();
    }

    @SuppressWarnings("unused")
    private List<TaskDto> fallbackListTasks(Throwable ex) {
        log.warn("Circuit breaker fallback for listTasks: {}", ex.toString());
        return List.of();
    }

    @CircuitBreaker(name = CB_NAME)
    public TaskDto getTask(UUID id) {
        return get("/api/tasks/" + id, new ParameterizedTypeReference<TaskDto>() {});
    }

    @CircuitBreaker(name = CB_NAME)
    public TaskDto cancelTask(UUID id) {
        return restClient.post()
                .uri("/api/tasks/" + id + "/cancel")
                .header("Authorization", "Bearer " + accessToken())
                .retrieve()
                .body(TaskDto.class);
    }

    @CircuitBreaker(name = CB_NAME)
    public byte[] taskResultsJson(UUID id) {
        return restClient.get()
                .uri("/api/tasks/" + id + "/results")
                .header("Authorization", "Bearer " + accessToken())
                .retrieve()
                .body(byte[].class);
    }

    public record ImagePayload(byte[] bytes, String contentType) {
    }

    @CircuitBreaker(name = CB_NAME)
    public ImagePayload taskImage(UUID id, String relativePath) {
        // Use UriBuilder.pathSegment(...) so each segment is URL-encoded exactly
        // once. Doing the encoding manually + passing to .uri(String) caused the
        // template processor to re-encode percent signs, producing %2520/%252520.
        String[] segments = relativePath.split("/");
        var resp = restClient.get()
                .uri(b -> b.path("/api/tasks/{id}/images").pathSegment(segments).build(id))
                .header("Authorization", "Bearer " + accessToken())
                .retrieve()
                .toEntity(byte[].class);
        String ct = resp.getHeaders().getContentType() != null
                ? resp.getHeaders().getContentType().toString()
                : "application/octet-stream";
        return new ImagePayload(resp.getBody(), ct);
    }

    @CircuitBreaker(name = CB_NAME)
    public byte[] taskLabelsZip(UUID id) {
        return restClient.get()
                .uri("/api/tasks/" + id + "/labels.zip")
                .header("Authorization", "Bearer " + accessToken())
                .retrieve()
                .body(byte[].class);
    }

    @CircuitBreaker(name = CB_NAME)
    public TaskDto createTask(UUID modelId, UUID datasetId) {
        return restClient.post()
                .uri("/api/tasks")
                .header("Authorization", "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("modelId", modelId, "datasetId", datasetId))
                .retrieve()
                .body(TaskDto.class);
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "fallbackGetPrefs")
    public NotificationPrefsDto getNotificationPrefs() {
        return get("/api/me/notifications", new ParameterizedTypeReference<NotificationPrefsDto>() {});
    }

    @SuppressWarnings("unused")
    private NotificationPrefsDto fallbackGetPrefs(Throwable ex) {
        log.warn("Circuit breaker fallback for getNotificationPrefs: {}", ex.toString());
        return new NotificationPrefsDto(false, false, "");
    }

    @CircuitBreaker(name = CB_NAME)
    public NotificationPrefsDto updateNotificationPrefs(boolean emailEnabled,
                                                       boolean telegramEnabled,
                                                       String telegramChatId) {
        return restClient.put()
                .uri("/api/me/notifications")
                .header("Authorization", "Bearer " + accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "emailEnabled", emailEnabled,
                        "telegramEnabled", telegramEnabled,
                        "telegramChatId", telegramChatId == null ? "" : telegramChatId))
                .retrieve()
                .body(NotificationPrefsDto.class);
    }

    private <T> T get(String path, ParameterizedTypeReference<T> typeRef) {
        return restClient.get()
                .uri(path)
                .header("Authorization", "Bearer " + accessToken())
                .retrieve()
                .body(typeRef);
    }

    private <T> T uploadMultipart(String path, String filePart, MultipartFile file,
                                  Map<String, String> formFields, Class<T> responseType) throws IOException {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        formFields.forEach(body::add);
        InputStream stream = file.getInputStream();
        body.add(filePart, new InputStreamResource(stream) {
            @Override public String getFilename() { return file.getOriginalFilename(); }
            @Override public long contentLength() { return file.getSize(); }
        });
        return restClient.post()
                .uri(path)
                .header("Authorization", "Bearer " + accessToken())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .onStatus(status -> status.value() == HttpStatus.PAYLOAD_TOO_LARGE.value(),
                        (req, resp) -> { throw new BackendException("File too large"); })
                .body(responseType);
    }

    private void deleteResource(String path) {
        restClient.delete()
                .uri(path)
                .header("Authorization", "Bearer " + accessToken())
                .retrieve()
                .toBodilessEntity();
    }

    private String accessToken() {
        OAuth2AuthenticationToken auth = (OAuth2AuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new BackendException("Not authenticated");
        }
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest
                .withClientRegistrationId(auth.getAuthorizedClientRegistrationId())
                .principal(auth)
                .build();
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
        if (client == null || client.getAccessToken() == null) {
            log.warn("Authorization failed for {} (likely refresh-token expired)", auth.getName());
            throw new BackendException("Session expired, please re-login");
        }
        return client.getAccessToken().getTokenValue();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelDto(UUID id, String name, long sizeBytes,
                           @JsonProperty("inputShape") String inputShape,
                           @JsonProperty("outputShape") String outputShape,
                           Instant uploadedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DatasetDto(UUID id, String name, String kind,
                             long sizeBytes, Integer fileCount, Instant uploadedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NotificationPrefsDto(boolean emailEnabled, boolean telegramEnabled, String telegramChatId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskDto(UUID id, UUID modelId, UUID datasetId, String status,
                          int progressPct, String errorMessage,
                          java.math.BigDecimal accuracy,
                          Instant createdAt, Instant startedAt, Instant finishedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Page<T>(List<T> content) {
    }

    public static class BackendException extends RuntimeException {
        public BackendException(String message) {
            super(message);
        }
    }
}
