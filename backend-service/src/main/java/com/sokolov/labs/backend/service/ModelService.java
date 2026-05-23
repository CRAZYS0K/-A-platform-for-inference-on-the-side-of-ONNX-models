package com.sokolov.labs.backend.service;

import com.sokolov.labs.backend.domain.Model;
import com.sokolov.labs.backend.domain.ModelRepository;
import com.sokolov.labs.backend.storage.ObjectStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Service
public class ModelService {

    private final ModelRepository repository;
    private final ObjectStorage storage;
    private final OnnxValidator validator;

    public ModelService(ModelRepository repository, ObjectStorage storage, OnnxValidator validator) {
        this.repository = repository;
        this.storage = storage;
        this.validator = validator;
    }

    @Transactional
    public Model upload(UUID ownerId, String name, MultipartFile file) throws IOException {
        validateFilename(file);
        if (file.isEmpty()) {
            throw new OnnxValidator.InvalidOnnxModelException("Файл пустой");
        }
        byte[] bytes = file.getBytes();
        OnnxValidator.ModelSchema schema = validator.validate(bytes);

        UUID id = UUID.randomUUID();
        String s3Key = "models/" + ownerId + "/" + id + ".onnx";

        storage.upload(s3Key, new ByteArrayInputStream(bytes), bytes.length, "application/octet-stream");

        Model model = new Model(id, ownerId, name, s3Key, bytes.length,
                schema.inputName(), schema.outputName(), schema.inputShape(), schema.outputShape(),
                Instant.now());
        return repository.save(model);
    }

    public Page<Model> list(UUID ownerId, Pageable pageable) {
        return repository.findByOwnerId(ownerId, pageable);
    }

    public Model get(UUID ownerId, UUID id) {
        return repository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ModelNotFoundException(id));
    }

    public String downloadUrl(UUID ownerId, UUID id) {
        Model model = get(ownerId, id);
        return storage.presignedGetUrl(model.getS3Key());
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        Model model = get(ownerId, id);
        storage.delete(model.getS3Key());
        repository.delete(model);
    }

    private static void validateFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            throw new OnnxValidator.InvalidOnnxModelException("Имя файла отсутствует");
        }
        if (!name.toLowerCase().endsWith(".onnx")) {
            throw new OnnxValidator.InvalidOnnxModelException(
                    "Ожидается файл с расширением .onnx, получено: " + name);
        }
    }

    public static class ModelNotFoundException extends RuntimeException {
        public ModelNotFoundException(UUID id) {
            super("Model not found: " + id);
        }
    }
}
