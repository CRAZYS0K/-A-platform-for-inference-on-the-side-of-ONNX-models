package com.sokolov.labs.backend.web;

import com.sokolov.labs.backend.domain.InferenceTask;
import com.sokolov.labs.backend.domain.UserAccount;
import com.sokolov.labs.backend.service.TaskService;
import com.sokolov.labs.backend.service.UserAccountService;
import com.sokolov.labs.backend.storage.ObjectStorage;
import com.sokolov.labs.shared.dto.TaskStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final UserAccountService userAccountService;
    private final ObjectStorage storage;

    public TaskController(TaskService taskService, UserAccountService userAccountService,
                          ObjectStorage storage) {
        this.taskService = taskService;
        this.userAccountService = userAccountService;
        this.storage = storage;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody CreateTaskRequest request) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        InferenceTask task = taskService.create(user.getId(), request.modelId(), request.datasetId());
        return ResponseEntity.created(URI.create("/api/tasks/" + task.getId()))
                .body(TaskResponse.from(task));
    }

    @GetMapping
    public Page<TaskResponse> list(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        return taskService.list(user.getId(), pageable).map(TaskResponse::from);
    }

    @GetMapping("/{id}")
    public TaskResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        return TaskResponse.from(taskService.get(user.getId(), id));
    }

    @GetMapping(value = "/{id}/results", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<InputStreamResource> results(@AuthenticationPrincipal Jwt jwt,
                                                       @PathVariable UUID id) throws IOException {
        UserAccount user = userAccountService.findOrCreate(jwt);
        InferenceTask task = taskService.get(user.getId(), id);
        if (task.getResultS3Key() == null) {
            return ResponseEntity.notFound().build();
        }
        InputStream in = storage.download(task.getResultS3Key());
        String contentType = task.getResultS3Key().endsWith(".json")
                ? MediaType.APPLICATION_JSON_VALUE : "text/csv";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(new InputStreamResource(in));
    }

    @GetMapping("/{id}/images/**")
    public ResponseEntity<InputStreamResource> image(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID id,
                                                     HttpServletRequest request) throws IOException {
        UserAccount user = userAccountService.findOrCreate(jwt);
        taskService.get(user.getId(), id);
        String fullPath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        String marker = "/images/";
        int idx = fullPath.indexOf(marker);
        if (idx < 0) {
            return ResponseEntity.notFound().build();
        }
        String relative = fullPath.substring(idx + marker.length());
        if (relative.isBlank() || relative.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        String key = "results/" + user.getId() + "/" + id + "/images/" + relative;
        InputStream in = storage.download(key);
        MediaType mediaType = contentTypeFor(relative);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .body(new InputStreamResource(in));
    }

    private static MediaType contentTypeFor(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".bmp")) return MediaType.parseMediaType("image/bmp");
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG;
    }

    public record CreateTaskRequest(@NotNull UUID modelId, @NotNull UUID datasetId) {
    }

    public record TaskResponse(UUID id, UUID modelId, UUID datasetId,
                               TaskStatus status, int progressPct,
                               String errorMessage, BigDecimal accuracy,
                               Instant createdAt, Instant startedAt, Instant finishedAt) {
        static TaskResponse from(InferenceTask t) {
            return new TaskResponse(t.getId(), t.getModelId(), t.getDatasetId(),
                    t.getStatus(), t.getProgressPct(), t.getErrorMessage(), t.getAccuracy(),
                    t.getCreatedAt(), t.getStartedAt(), t.getFinishedAt());
        }
    }
}
