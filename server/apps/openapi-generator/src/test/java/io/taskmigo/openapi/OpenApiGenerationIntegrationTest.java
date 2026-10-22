package io.taskmigo.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.scalar.maven.webmvc.ScalarWebMvcController;
import com.scalar.maven.webmvc.SpringBootScalarProperties;
import io.swagger.v3.core.util.Yaml;
import io.taskmigo.TaskmigoApplication;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(
    classes = TaskmigoApplication.class,
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
    OpenApiGenerationConfiguration.class,
    OpenApiGenerationIntegrationTest.PostgresTestConfiguration.class,
    OpenApiGenerationIntegrationTest.ScalarTestConfiguration.class,
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OpenApiGenerationIntegrationTest {

    private static final Path OPENAPI_FILE = Path.of(
        Objects.requireNonNull(System.getProperty("taskmigo.openapi.output"))
    );

    @LocalServerPort
    int port;

    /// Verifies that the Scalar API reference UI is reachable through its configured HTTP route.
    @Test
    @DisplayName("serves the Scalar API reference UI when the docs route is requested")
    void shouldServeScalarUiWhenDocsRouteIsRequested() {
        RestClient http = this.http();

        ResponseEntity<String> response = http.get().uri("/api/docs").retrieve().toEntity(String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Taskmigo API Reference");
    }

    /// Verifies that Scalar points at a reachable OpenAPI specification.
    @Test
    @DisplayName("serves the OpenAPI specification when Scalar requests its configured source")
    void shouldServeOpenApiSpecificationWhenScalarSourceIsRequested() {
        RestClient http = this.http();

        ResponseEntity<String> ui = http.get().uri("/api/docs").retrieve().toEntity(String.class);
        ResponseEntity<String> specification = http
            .get()
            .uri("/api/docs/openapi.yaml")
            .retrieve()
            .toEntity(String.class);

        assertThat(ui.getBody()).contains("/api/docs/openapi.yaml");
        assertThat(specification.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(specification.getBody()).contains("openapi:");
    }

    /// Verifies that generated filter metadata is operation-scoped and reflects each Query Schema exactly.
    @Test
    @DisplayName("documents operation-specific filterBy fields and operators")
    void shouldDocumentOperationSpecificFilterByCapabilities() {
        JsonNode document = this.generatedOpenApiDocument();

        JsonNode users = filterByExtension(document, "/api/v0/users");
        JsonNode groups = filterByExtension(document, "/api/v0/groups");

        assertThat(textValues(users.path("username"))).containsExactly("EQ", "NE", "CONTAINS");
        assertThat(textValues(users.path("retainedAt"))).containsExactly("EQ", "NE", "GT", "GTE", "LT", "LTE");
        assertThat(users.has("description")).isFalse();
        assertThat(textValues(groups.path("description"))).containsExactly("EQ", "NE", "CONTAINS");
        assertThat(groups.has("retainedAt")).isFalse();
    }

    /// Verifies that the extension uses direct field names without wrapper or expression-root prefixes.
    @Test
    @DisplayName("documents concise filterBy metadata")
    void shouldDocumentConciseFilterByMetadata() {
        JsonNode users = filterByExtension(this.generatedOpenApiDocument(), "/api/v0/users");

        assertThat(users.has("username")).isTrue();
        assertThat(users.has("object.username")).isFalse();
        assertThat(users.has("fields")).isFalse();
        assertThat(users.has("operatorSyntax")).isFalse();
    }

    /// Verifies that every generated filterBy parameter includes machine-readable capability metadata.
    @Test
    @DisplayName("documents every generated filterBy query parameter")
    void shouldDocumentEveryFilterByParameter() {
        JsonNode document = this.generatedOpenApiDocument();
        List<JsonNode> filterParameters = new ArrayList<>();

        document.path("paths").forEach(path ->
            path.forEach(operation ->
                operation.path("parameters").forEach(parameter -> {
                    if (
                        "filterBy".equals(parameter.path("name").asText()) &&
                        "query".equals(parameter.path("in").asText())
                    ) {
                        filterParameters.add(parameter);
                    }
                })
            )
        );

        assertThat(filterParameters).isNotEmpty();
        assertThat(filterParameters).allSatisfy(parameter -> {
            JsonNode extension = parameter.path("x-filter-by");
            assertThat(extension.isObject()).isTrue();
            assertThat(extension.isEmpty()).isFalse();
        });
    }

    /// Verifies that generated sort metadata is operation-scoped and sourced from Query Schema sortability.
    @Test
    @DisplayName("documents operation-specific sortBy fields")
    void shouldDocumentOperationSpecificSortCapabilities() {
        JsonNode document = this.generatedOpenApiDocument();

        assertThat(textValues(sortByExtension(document, "/api/v0/users"))).containsExactly(
            "firstName",
            "id",
            "lastName",
            "retainedAt",
            "status",
            "username"
        );
        assertThat(textValues(sortByExtension(document, "/api/v0/groups"))).containsExactly(
            "code",
            "description",
            "displayName",
            "id"
        );
        assertThat(textValues(sortByExtension(document, "/api/v0/tickets"))).containsExactly(
            "createdAt",
            "status",
            "title",
            "updatedAt"
        );
    }

    /// Verifies that every generated sortBy parameter advertises its exact machine-readable allow-list.
    @Test
    @DisplayName("documents every generated sortBy query parameter")
    void shouldDocumentEverySortParameter() {
        JsonNode document = this.generatedOpenApiDocument();
        List<JsonNode> sortParameters = new ArrayList<>();

        document.path("paths").forEach(path ->
            path.forEach(operation ->
                operation.path("parameters").forEach(parameter -> {
                    if (
                        "sortBy".equals(parameter.path("name").asText()) &&
                        "query".equals(parameter.path("in").asText())
                    ) {
                        sortParameters.add(parameter);
                    }
                })
            )
        );

        assertThat(sortParameters).isNotEmpty();
        assertThat(sortParameters).allSatisfy(parameter -> {
            JsonNode extension = parameter.path("x-sort-by");
            assertThat(extension.isArray()).isTrue();
            assertThat(extension.isEmpty()).isFalse();
        });
    }

    @AfterAll
    void writeCommittedOpenApiYaml() throws IOException {
        JsonNode generated = this.generatedOpenApiDocument();
        ObjectNode document = (ObjectNode) generated;
        document.remove("servers");
        this.sortResponseCodes(document);
        Files.createDirectories(Objects.requireNonNull(OPENAPI_FILE.getParent()));
        Files.writeString(OPENAPI_FILE, "---\n" + Yaml.pretty(document), StandardCharsets.UTF_8);
    }

    private JsonNode generatedOpenApiDocument() {
        String openApi = Objects.requireNonNull(
            this.http().get().uri("/api/docs/openapi.json/v0").retrieve().body(String.class)
        );
        return this.document(openApi);
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

    private static JsonNode filterByExtension(JsonNode document, String path) {
        for (JsonNode parameter : document.path("paths").path(path).path("get").path("parameters")) {
            if ("filterBy".equals(parameter.path("name").asText()) && "query".equals(parameter.path("in").asText())) {
                JsonNode extension = parameter.path("x-filter-by");
                if (extension.isMissingNode()) {
                    throw new AssertionError(path + " filterBy parameter must define x-filter-by");
                }
                return extension;
            }
        }
        throw new AssertionError(path + " must define a filterBy query parameter");
    }

    private static JsonNode sortByExtension(JsonNode document, String path) {
        for (JsonNode parameter : document.path("paths").path(path).path("get").path("parameters")) {
            if ("sortBy".equals(parameter.path("name").asText()) && "query".equals(parameter.path("in").asText())) {
                JsonNode extension = parameter.path("x-sort-by");
                if (extension.isMissingNode()) {
                    throw new AssertionError(path + " sortBy parameter must define x-sort-by");
                }
                return extension;
            }
        }
        throw new AssertionError(path + " must define a sortBy query parameter");
    }

    private static List<String> textValues(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private RestClient http() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + this.port)
            .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PostgresTestConfiguration {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgres() {
            return new PostgreSQLContainer("postgres:18.4-alpine");
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
