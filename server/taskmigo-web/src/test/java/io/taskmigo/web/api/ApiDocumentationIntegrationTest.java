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
        "taskmigo.internal.clients[0].id=integration-client",
        "taskmigo.internal.clients[0].secret=integration-secret",
        "taskmigo.internal.clients[0].enabled=true",
        "taskmigo.internal.clients[0].generation=1",
        "taskmigo.security.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.security.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
class ApiDocumentationIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void rendersGeneratedOpenApiDocumentAndReference() {
        String openApi = Objects.requireNonNull(
            http().get().uri("/api/docs/openapi.json").retrieve().body(String.class)
        );
        assertThat(openApi)
            .contains("\"title\":\"Taskmigo API\"")
            .contains("\"taskmigoOAuth\"")
            .contains("\"/api/v0/permissions\"");

        String reference = Objects.requireNonNull(http().get().uri("/api/docs").retrieve().body(String.class));
        assertThat(reference).contains("Taskmigo API Reference").contains("/api/docs/openapi.json");
    }

    private RestClient http() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
    }
}
