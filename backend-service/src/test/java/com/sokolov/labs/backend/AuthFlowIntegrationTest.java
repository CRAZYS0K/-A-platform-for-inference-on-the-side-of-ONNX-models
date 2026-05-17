package com.sokolov.labs.backend;

import com.sokolov.labs.backend.domain.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIf(value = "com.sokolov.labs.backend.IntegrationTestBase#dockerAvailable",
        disabledReason = "Docker is not available — skipping Testcontainers integration test")
class AuthFlowIntegrationTest extends IntegrationTestBase {

    @LocalServerPort
    int port;

    @Autowired
    UserAccountRepository userAccountRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    void unauthenticatedRequestIsRejected() {
        ResponseEntity<String> response;
        try {
            response = restTemplate.getForEntity("http://localhost:" + port + "/api/me", String.class);
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            response = ResponseEntity.status(ex.getStatusCode()).build();
        }
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authenticatedRequestReturnsUserAndPersistsAccount() {
        String accessToken = obtainAccessToken("alice", "secret");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "http://localhost:" + port + "/api/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new org.springframework.core.ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("email")).isEqualTo("alice@example.com");
        assertThat(response.getBody().get("displayName")).isEqualTo("alice");

        long userCount = userAccountRepository.count();
        assertThat(userCount).isEqualTo(1);
    }

    private String obtainAccessToken(String username, String password) {
        String tokenUrl = issuerUri() + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "password");
        form.add("client_id", "test-client");
        form.add("username", username);
        form.add("password", password);
        form.add("scope", "openid profile email");

        ResponseEntity<Map<String, Object>> tokenResp = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                new HttpEntity<>(form, headers),
                new org.springframework.core.ParameterizedTypeReference<>() {}
        );
        assertThat(tokenResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) tokenResp.getBody().get("access_token");
    }
}
