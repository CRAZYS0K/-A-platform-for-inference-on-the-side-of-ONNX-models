package com.sokolov.labs.backend.service;

import com.sokolov.labs.backend.domain.Dataset;
import com.sokolov.labs.backend.domain.DatasetRepository;
import com.sokolov.labs.backend.storage.ObjectStorage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class DatasetService {

    private final DatasetRepository repository;
    private final ObjectStorage storage;

    public DatasetService(DatasetRepository repository, ObjectStorage storage) {
        this.repository = repository;
        this.storage = storage;
    }

    @Transactional
    public Dataset upload(UUID ownerId, String name, Dataset.Kind kind, MultipartFile file) throws IOException {
        validateZipFile(file);
        int fileCount = countZipEntries(file);
        if (fileCount == 0) {
            throw new InvalidDatasetException("ZIP-архив пустой");
        }

        UUID id = UUID.randomUUID();
        String s3Key = "datasets/" + ownerId + "/" + id + ".zip";

        try (InputStream in = file.getInputStream()) {
            storage.upload(s3Key, in, file.getSize(), "application/zip");
        }

        Dataset dataset = new Dataset(id, ownerId, name, kind, s3Key,
                file.getSize(), fileCount, Instant.now());
        return repository.save(dataset);
    }

    public Page<Dataset> list(UUID ownerId, Pageable pageable) {
        return repository.findByOwnerId(ownerId, pageable);
    }

    public Dataset get(UUID ownerId, UUID id) {
        return repository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new DatasetNotFoundException(id));
    }

    public String downloadUrl(UUID ownerId, UUID id) {
        Dataset ds = get(ownerId, id);
        return storage.presignedGetUrl(ds.getS3Key());
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        Dataset ds = get(ownerId, id);
        storage.delete(ds.getS3Key());
        repository.delete(ds);
    }

    private static void validateZipFile(MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw new InvalidDatasetException("Имя файла отсутствует");
        }
        if (!original.toLowerCase().endsWith(".zip")) {
            throw new InvalidDatasetException(
                    "Ожидается ZIP-архив, получено: " + original);
        }
        if (file.isEmpty()) {
            throw new InvalidDatasetException("Файл пустой");
        }
        // ZIP magic bytes: PK\003\004 (local file header) or PK\005\006 (empty zip end record).
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(4);
            if (head.length < 4
                    || head[0] != (byte) 'P' || head[1] != (byte) 'K'
                    || !(head[2] == 0x03 || head[2] == 0x05)) {
                throw new InvalidDatasetException(
                        "Файл не является ZIP-архивом (неверная сигнатура)");
            }
        }
    }

    private static int countZipEntries(MultipartFile file) throws IOException {
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    count++;
                }
                zip.closeEntry();
            }
        }
        return count;
    }

    public static class InvalidDatasetException extends RuntimeException {
        public InvalidDatasetException(String message) {
            super(message);
        }
    }

    public static class DatasetNotFoundException extends RuntimeException {
        public DatasetNotFoundException(UUID id) {
            super("Dataset not found: " + id);
        }
    }
}
