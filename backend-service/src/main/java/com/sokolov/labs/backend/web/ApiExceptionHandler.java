package com.sokolov.labs.backend.web;

import com.sokolov.labs.backend.service.DatasetService;
import com.sokolov.labs.backend.service.ModelService;
import com.sokolov.labs.backend.service.OnnxValidator;
import com.sokolov.labs.backend.service.TaskService;
import com.sokolov.labs.backend.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({ModelService.ModelNotFoundException.class,
            DatasetService.DatasetNotFoundException.class,
            TaskService.TaskNotFoundException.class})
    public ResponseEntity<Map<String, Object>> notFound(RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({OnnxValidator.InvalidOnnxModelException.class, DatasetService.InvalidDatasetException.class})
    public ResponseEntity<Map<String, Object>> badRequest(RuntimeException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds allowed size");
    }

    @ExceptionHandler(ObjectStorage.StorageException.class)
    public ResponseEntity<Map<String, Object>> storageError(ObjectStorage.StorageException ex) {
        log.error("Storage error", ex);
        return error(HttpStatus.BAD_GATEWAY, "Object storage failure");
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "timestamp", Instant.now().toString()
        ));
    }
}
