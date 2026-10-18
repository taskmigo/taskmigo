package io.taskmigo.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scalar.maven.webmvc.ScalarWebMvcController;
import com.scalar.maven.webmvc.SpringBootScalarProperties;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.models.info.Info;
import io.taskmigo.PostgresTestConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.api-docs.path=/api/docs/openapi.json",
        "springdoc.api-docs.version=openapi-3-1",
        "springdoc.default-produces-media-type=application/json",
        "taskmigo.oauth.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.oauth.signing-key-auto-create=true",
    }
)
@Import({
    PostgresTestConfiguration.class,
    OpenApiGenerationIntegrationTest.OpenApiGenerationConfiguration.class,
    OpenApiGenerationIntegrationTest.ScalarTestConfiguration.class,
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiGenerationIntegrationTest {

    private static final Path OPENAPI_DIRECTORY = Path.of("src/main/resources/static/api/docs");
    private static final Path OPENAPI_FILE = OPENAPI_DIRECTORY.resolve("openapi.yaml");

    @LocalServerPort
    int port;

    /**
     * Verifies that the Scalar API reference UI is reachable through its configured HTTP route.
     *
     * Given: the web application is started with Scalar enabled for the test context.
     * Expect: GET /api/docs returns HTTP 200 and the configured Taskmigo API Reference page.
     */
    @Test
    @DisplayName("serves the Scalar API reference UI when the docs route is requested")
    void shouldServeScalarUiWhenDocsRouteIsRequested() {
        // Arrange
        RestClient http = this.http();

        // Act
        ResponseEntity<String> response = http.get().uri("/api/docs").retrieve().toEntity(String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Taskmigo API Reference");
    }

    /**
     * Verifies that Scalar points at a reachable OpenAPI specification.
     *
     * Given: the Scalar UI is configured to use /api/docs/openapi.yaml.
     * Expect: the UI references that URL and GET /api/docs/openapi.yaml returns an OpenAPI document with HTTP 200.
     */
    @Test
    @DisplayName("serves the OpenAPI specification when Scalar requests its configured source")
    void shouldServeOpenApiSpecificationWhenScalarSourceIsRequested() {
        // Arrange
        RestClient http = this.http();

        // Act
        ResponseEntity<String> ui = http.get().uri("/api/docs").retrieve().toEntity(String.class);
        ResponseEntity<String> specification = http
            .get()
            .uri("/api/docs/openapi.yaml")
            .retrieve()
            .toEntity(String.class);

        // Assert
        assertThat(ui.getBody()).contains("/api/docs/openapi.yaml");
        assertThat(specification.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(specification.getBody()).contains("openapi:");
    }

    @AfterAll
    void writeCommittedOpenApiYaml() throws IOException {
        String generated = this.generatedOpenApiYaml();
        Files.createDirectories(OPENAPI_DIRECTORY);
        Files.writeString(OPENAPI_FILE, generated, StandardCharsets.UTF_8);
    }

    private String generatedOpenApiYaml() {
        String openApi = Objects.requireNonNull(
            this.http().get().uri("/api/docs/openapi.json/v0").retrieve().body(String.class)
        );
        ObjectNode document = (ObjectNode) this.document(openApi);
        document.remove("servers");
        this.sortResponseCodes(document);
        return "---\n" + Yaml.pretty(document);
    }

    private void sortResponseCodes(ObjectNode document) {
        document
            .path("paths")
            .properties()
            .forEach(path ->
                path
                    .getValue()
                    .properties()
                    .forEach(operation -> {
                        if (operation.getValue().path("responses") instanceof ObjectNode responses) {
                            var sorted = responses.properties().stream().sorted(Map.Entry.comparingByKey()).toList();
                            responses.removeAll();
                            sorted.forEach(entry -> responses.set(entry.getKey(), entry.getValue()));
                        }
                    })
            );
    }

    private JsonNode document(String openApi) {
        try {
            return new ObjectMapper().readTree(openApi);
        } catch (Exception exception) {
            throw new AssertionError("OpenAPI document must be valid JSON", exception);
        }
    }

    private RestClient http() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + this.port)
            .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OpenApiGenerationConfiguration {

        @Bean
        GroupedOpenApi v0OpenApi() {
            return GroupedOpenApi.builder()
                .group("v0")
                .pathsToMatch("/api/v{version}/**")
                .addOpenApiCustomizer(openApi ->
                    openApi.info(new Info().title("Taskmigo API").version("v0").description("Taskmigo API"))
                )
                .build();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SpringBootScalarProperties.class)
    static class ScalarTestConfiguration {

        @Bean
        @ConditionalOnMissingBean(ScalarWebMvcController.class)
        ScalarWebMvcController scalarWebMvcController() {
            return new ScalarWebMvcController();
        }
    }
}
