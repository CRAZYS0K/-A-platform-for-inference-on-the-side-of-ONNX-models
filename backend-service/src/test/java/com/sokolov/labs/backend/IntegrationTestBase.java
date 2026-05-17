package com.sokolov.labs.backend;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@EnabledIf("com.sokolov.labs.backend.IntegrationTestBase#dockerAvailable")
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES;
    static final KeycloakContainer KEYCLOAK;
    static final GenericContainer<?> MINIO;
    static final KafkaContainer KAFKA;

    static final String MINIO_USER = "minioadmin";
    static final String MINIO_PASSWORD = "minioadmin";

    static {
        if (dockerAvailable()) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("onnxi")
                    .withUsername("onnxi")
                    .withPassword("onnxi");
            KEYCLOAK = new KeycloakContainer("quay.io/keycloak/keycloak:26.0")
                    .withRealmImportFile("/keycloak/test-realm.json");
            MINIO = new GenericContainer<>("minio/minio:RELEASE.2024-11-07T00-52-20Z")
                    .withEnv("MINIO_ROOT_USER", MINIO_USER)
                    .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
                    .withCommand("server", "/data")
                    .withExposedPorts(9000)
                    .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
            KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));
            POSTGRES.start();
            KEYCLOAK.start();
            MINIO.start();
            KAFKA.start();
        } else {
            POSTGRES = null;
            KEYCLOAK = null;
            MINIO = null;
            KAFKA = null;
        }
    }

    public static boolean dockerAvailable() {
        try {
            DockerClientFactory.instance().client().pingCmd().exec();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
        if (KEYCLOAK != null) {
            registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", IntegrationTestBase::issuerUri);
            registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                    () -> issuerUri() + "/protocol/openid-connect/certs");
        }
        if (MINIO != null) {
            registry.add("minio.endpoint",
                    () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
            registry.add("minio.access-key", () -> MINIO_USER);
            registry.add("minio.secret-key", () -> MINIO_PASSWORD);
            registry.add("minio.bucket", () -> "onnxi-test");
        }
        if (KAFKA != null) {
            registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        }
    }

    protected static String issuerUri() {
        return KEYCLOAK.getAuthServerUrl() + "/realms/onnxi";
    }
}
