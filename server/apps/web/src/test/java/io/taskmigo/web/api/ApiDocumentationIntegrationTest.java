package io.taskmigo.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskmigo.PostgresTestConfiguration;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("renders the versioned OpenAPI document and its references")
    void shouldRenderVersionedOpenApiDocumentWhenApiDocsAreRequested() {
        String openApi = Objects.requireNonNull(
            this.http().get().uri("/api/docs/openapi.json/v0").retrieve().body(String.class)
        );
        JsonNode document = this.document(openApi);
        JsonNode responseSchema = document.at("/paths/~1api~1v0~1groups/get/responses/200/content/*~1*/schema");
        JsonNode groupListResponse = this.component(document, responseSchema.path("$ref").asText());
        JsonNode groupCreateRequest = document.at(
            "/paths/~1api~1v0~1groups/post/requestBody/content/application~1json/schema/$ref"
        );
        JsonNode groupListParameters = document.at("/paths/~1api~1v0~1groups/get/parameters");

        assertThat(openApi)
            .contains("\"title\":\"Taskmigo API\"")
            .contains("\"version\":\"v0\"")
            .contains("\"taskmigoOAuth\"")
            .contains("\"statusCode\"")
            .contains("\"formErrors\"")
            .contains("\"startedAt\"")
            .contains("\"duration\"")
            .contains("\"description\":\"Server processing time in milliseconds\"")
            .contains("\"description\":\"One-based index of the current page\"")
            .contains("\"description\":\"Maximum number of items in each page\"");
        assertThat(responseSchema.path("$ref").asText()).contains("ApiResponse");
        assertThat(groupListResponse.at("/properties/data/type").toString()).contains("\"array\"");
        assertThat(groupListResponse.at("/properties/data/items/$ref").asText()).contains("GroupInfo");
        assertThat(groupListResponse.at("/properties/meta/$ref").asText()).contains("OffsetMeta");
        assertThat(groupCreateRequest.asText()).isEqualTo("#/components/schemas/CreateGroupRequest");
        assertThat(groupListParameters.toString()).contains("\"name\":\"page\"").contains("\"name\":\"pageSize\"");
        assertThat(groupListParameters.toString()).doesNotContain("\"name\":\"pagination\"");

        String reference = Objects.requireNonNull(this.http().get().uri("/api/docs").retrieve().body(String.class));
        assertThat(reference).contains("Taskmigo API Reference").contains("/api/docs/openapi.json/v0");
    }

    private JsonNode document(String openApi) {
        try {
            return new ObjectMapper().readTree(openApi);
        } catch (Exception exception) {
            throw new AssertionError("OpenAPI document must be valid JSON", exception);
        }
    }

    private JsonNode component(JsonNode document, String reference) {
        return document.at("/components/schemas/" + reference.substring(reference.lastIndexOf('/') + 1));
    }

    private RestClient http() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + this.port)
            .build();
    }
}
