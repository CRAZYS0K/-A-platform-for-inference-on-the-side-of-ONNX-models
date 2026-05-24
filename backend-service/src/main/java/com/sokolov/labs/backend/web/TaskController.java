package com.sokolov.labs.backend.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sokolov.labs.backend.domain.InferenceTask;
import com.sokolov.labs.backend.domain.UserAccount;
import com.sokolov.labs.backend.service.TaskService;
import com.sokolov.labs.backend.service.UserAccountService;
import com.sokolov.labs.backend.storage.ObjectStorage;
import com.sokolov.labs.shared.dto.TaskResultsPayload;
import com.sokolov.labs.shared.dto.TaskStatus;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final UserAccountService userAccountService;
    private final ObjectStorage storage;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @PostMapping("/{id}/cancel")
    public TaskResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        return TaskResponse.from(taskService.cancel(user.getId(), id));
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

    @GetMapping("/{id}/labels.zip")
    public ResponseEntity<byte[]> labelsZip(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable UUID id) throws IOException {
        UserAccount user = userAccountService.findOrCreate(jwt);
        InferenceTask task = taskService.get(user.getId(), id);
        if (task.getResultS3Key() == null || !task.getResultS3Key().endsWith(".json")) {
            return ResponseEntity.notFound().build();
        }
        TaskResultsPayload payload;
        try (InputStream in = storage.download(task.getResultS3Key())) {
            payload = objectMapper.readValue(in, TaskResultsPayload.class);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            for (TaskResultsPayload.ImageResult item : payload.items()) {
                String txtName = stripExtension(item.filename()) + ".txt";
                zip.putNextEntry(new ZipEntry(txtName));
                zip.write(buildYoloLabels(item).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"task-" + id + "-labels.zip\"")
                .body(baos.toByteArray());
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    private static String buildYoloLabels(TaskResultsPayload.ImageResult item) {
        StringBuilder sb = new StringBuilder();
        double w = item.origWidth();
        double h = item.origHeight();
        if (w <= 0 || h <= 0) return "";
        for (TaskResultsPayload.Detection d : item.detections()) {
            double cx = (d.x1() + d.x2()) / 2.0 / w;
            double cy = (d.y1() + d.y2()) / 2.0 / h;
            double bw = (d.x2() - d.x1()) / w;
            double bh = (d.y2() - d.y1()) / h;
            sb.append(d.classId())
                    .append(' ').append(fmt(cx))
                    .append(' ').append(fmt(cy))
                    .append(' ').append(fmt(bw))
                    .append(' ').append(fmt(bh));
            List<Double> kpts = d.keypoints();
            if (kpts != null && !kpts.isEmpty()) {
                int stride = (kpts.size() % 3 == 0) ? 3 : 2;
                for (int i = 0; i + 1 < kpts.size(); i += stride) {
                    double kx = kpts.get(i) / w;
                    double ky = kpts.get(i + 1) / h;
                    sb.append(' ').append(fmt(kx)).append(' ').append(fmt(ky));
                    if (stride == 3 && i + 2 < kpts.size()) {
                        sb.append(' ').append(fmt(kpts.get(i + 2)));
                    }
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.6f", v);
    }

    @GetMapping("/{id}/images/{*path}")
    public ResponseEntity<InputStreamResource> image(@AuthenticationPrincipal Jwt jwt,
                                                     @PathVariable UUID id,
                                                     @PathVariable String path) throws IOException {
        UserAccount user = userAccountService.findOrCreate(jwt);
        taskService.get(user.getId(), id);
        String relative = path.startsWith("/") ? path.substring(1) : path;
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
