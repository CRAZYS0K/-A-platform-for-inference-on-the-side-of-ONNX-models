package com.sokolov.labs.backend.web;

import com.sokolov.labs.backend.domain.Dataset;
import com.sokolov.labs.backend.domain.UserAccount;
import com.sokolov.labs.backend.service.DatasetService;
import com.sokolov.labs.backend.service.UserAccountService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/datasets")
public class DatasetController {

    private final DatasetService datasetService;
    private final UserAccountService userAccountService;

    public DatasetController(DatasetService datasetService, UserAccountService userAccountService) {
        this.datasetService = datasetService;
        this.userAccountService = userAccountService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<DatasetResponse> upload(@AuthenticationPrincipal Jwt jwt,
                                                  @RequestParam @NotBlank @Size(max = 255) String name,
                                                  @RequestParam(defaultValue = "UNLABELED") Dataset.Kind kind,
                                                  @RequestParam("file") MultipartFile file) throws IOException {
        UserAccount user = userAccountService.findOrCreate(jwt);
        Dataset dataset = datasetService.upload(user.getId(), name, kind, file);
        return ResponseEntity
                .created(URI.create("/api/datasets/" + dataset.getId()))
                .body(DatasetResponse.from(dataset));
    }

    @GetMapping
    public Page<DatasetResponse> list(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        return datasetService.list(user.getId(), pageable).map(DatasetResponse::from);
    }

    @GetMapping("/{id}")
    public DatasetResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        return DatasetResponse.from(datasetService.get(user.getId(), id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Void> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        String url = datasetService.downloadUrl(user.getId(), id);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        datasetService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    public record DatasetResponse(UUID id, String name, Dataset.Kind kind,
                                  long sizeBytes, Integer fileCount, Instant uploadedAt) {
        static DatasetResponse from(Dataset d) {
            return new DatasetResponse(d.getId(), d.getName(), d.getKind(),
                    d.getSizeBytes(), d.getFileCount(), d.getUploadedAt());
        }
    }
}
