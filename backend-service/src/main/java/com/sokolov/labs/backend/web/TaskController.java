package com.sokolov.labs.backend.web;

import com.sokolov.labs.backend.domain.InferenceTask;
import com.sokolov.labs.backend.domain.UserAccount;
import com.sokolov.labs.backend.service.TaskService;
import com.sokolov.labs.backend.service.UserAccountService;
import com.sokolov.labs.shared.dto.TaskStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final UserAccountService userAccountService;

    public TaskController(TaskService taskService, UserAccountService userAccountService) {
        this.taskService = taskService;
        this.userAccountService = userAccountService;
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
