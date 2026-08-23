package io.taskmigo.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.PostgresTestConfiguration;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-id=integration-client",
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-secret=integration-secret",
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-authentication-methods=client_secret_basic",
        "spring.security.oauth2.authorizationserver.client.cli.registration.authorization-grant-types=client_credentials",
        "spring.security.oauth2.authorizationserver.client.cli.registration.scopes=taskmigo.api",
        "taskmigo.security.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.security.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
class ApiDocumentationIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void rendersVersionedOpenApiDocumentAndReference() {
        String openApi = Objects.requireNonNull(
            this.http().get().uri("/api/docs/openapi.json/v0").retrieve().body(String.class)
        );
        assertThat(openApi)
            .contains("\"title\":\"Taskmigo API\"")
            .contains("\"version\":\"v0\"")
            .contains("\"taskmigoOAuth\"")
            .contains("\"/api/v0/permissions\"")
            .contains("\"ApiResponse\"")
            .contains("\"status_code\"")
            .contains("\"form_errors\"")
            .contains("\"started_at\"")
            .contains("\"duration_ms\"")
            .contains("\"pagination\"");

        String reference = Objects.requireNonNull(this.http().get().uri("/api/docs").retrieve().body(String.class));
        assertThat(reference).contains("Taskmigo API Reference").contains("/api/docs/openapi.json/v0");
    }

    private RestClient http() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + this.port)
            .build();
    }
}
