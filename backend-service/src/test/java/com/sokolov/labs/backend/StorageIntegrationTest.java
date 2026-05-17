package com.sokolov.labs.backend;

import com.sokolov.labs.backend.domain.DatasetRepository;
import com.sokolov.labs.backend.domain.InferenceTaskRepository;
import com.sokolov.labs.backend.domain.ModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf(value = "com.sokolov.labs.backend.IntegrationTestBase#dockerAvailable",
        disabledReason = "Docker is not available — skipping Testcontainers integration test")
class StorageIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @Autowired
    DatasetRepository datasetRepository;

    @Autowired
    ModelRepository modelRepository;

    @Autowired
    InferenceTaskRepository taskRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @BeforeEach
    void clean() {
        taskRepository.deleteAll();
        datasetRepository.deleteAll();
        modelRepository.deleteAll();
    }

    @Test
    void datasetUploadStoresArchiveAndCountsEntries() throws IOException {
        String token = obtainAccessToken();

        byte[] zipBytes = buildZip(Map.of(
                "a.txt", "alpha".getBytes(),
                "b.txt", "beta".getBytes(),
                "c.txt", "gamma".getBytes()
        ));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", "mini-dataset");
        body.add("kind", "UNLABELED");
        body.add("file", namedZipResource(zipBytes, "mini.zip"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                "http://localhost:" + port + "/api/datasets",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                new org.springframework.core.ParameterizedTypeReference<>() {}
        );

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().get("name")).isEqualTo("mini-dataset");
        assertThat(((Number) resp.getBody().get("fileCount")).intValue()).isEqualTo(3);
        assertThat(((Number) resp.getBody().get("sizeBytes")).longValue()).isEqualTo(zipBytes.length);
        assertThat(datasetRepository.count()).isEqualTo(1);
    }

    @Test
    void modelUploadRejectsInvalidOnnxBytes() {
        String token = obtainAccessToken();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", "garbage");
        body.add("file", namedResource("not an onnx model".getBytes(), "broken.onnx", "application/octet-stream"));

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpStatus status;
        try {
            restTemplate.exchange(
                    "http://localhost:" + port + "/api/models",
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            status = HttpStatus.OK;
        } catch (HttpClientErrorException ex) {
            status = HttpStatus.valueOf(ex.getStatusCode().value());
        }

        assertThat(status).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(modelRepository.count()).isZero();
    }

    private String obtainAccessToken() {
        String tokenUrl = issuerUri() + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "test-client");
        form.add("username", "alice");
        form.add("password", "secret");
        form.add("scope", "openid profile email");

        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                new org.springframework.core.ParameterizedTypeReference<>() {}
        );
        return (String) resp.getBody().get("access_token");
    }

    private static byte[] buildZip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static Resource namedZipResource(byte[] bytes, String filename) {
        return namedResource(bytes, filename, "application/zip");
    }

    private static Resource namedResource(byte[] bytes, String filename, String contentType) {
        return new ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
            @Override public long contentLength() { return bytes.length; }
        };
    }
}
