package com.sokolov.labs.backend.web;

import com.sokolov.labs.backend.domain.Model;
import com.sokolov.labs.backend.domain.UserAccount;
import com.sokolov.labs.backend.service.ModelService;
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
@RequestMapping("/api/models")
public class ModelController {

    private final ModelService modelService;
    private final UserAccountService userAccountService;

    public ModelController(ModelService modelService, UserAccountService userAccountService) {
        this.modelService = modelService;
        this.userAccountService = userAccountService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ModelResponse> upload(@AuthenticationPrincipal Jwt jwt,
                                                @RequestParam @NotBlank @Size(max = 255) String name,
                                                @RequestParam("file") MultipartFile file) throws IOException {
        UserAccount user = userAccountService.findOrCreate(jwt);
        Model model = modelService.upload(user.getId(), name, file);
        return ResponseEntity
                .created(URI.create("/api/models/" + model.getId()))
                .body(ModelResponse.from(model));
    }

    @GetMapping
    public Page<ModelResponse> list(@AuthenticationPrincipal Jwt jwt, Pageable pageable) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        return modelService.list(user.getId(), pageable).map(ModelResponse::from);
    }

    @GetMapping("/{id}")
    public ModelResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        return ModelResponse.from(modelService.get(user.getId(), id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Void> download(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        String url = modelService.downloadUrl(user.getId(), id);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UserAccount user = userAccountService.findOrCreate(jwt);
        modelService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    public record ModelResponse(UUID id, String name, long sizeBytes,
                                String inputName, String outputName,
                                String inputShape, String outputShape,
                                Instant uploadedAt) {
        static ModelResponse from(Model m) {
            return new ModelResponse(m.getId(), m.getName(), m.getSizeBytes(),
                    m.getInputName(), m.getOutputName(),
                    m.getInputShape(), m.getOutputShape(),
                    m.getUploadedAt());
        }
    }
}
