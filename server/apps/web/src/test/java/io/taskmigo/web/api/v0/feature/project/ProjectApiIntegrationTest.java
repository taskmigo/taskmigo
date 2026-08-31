package io.taskmigo.web.api.v0.feature.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.history.ProjectHistory;
import io.taskmigo.organization.OrganizationService;
import io.taskmigo.project.ProjectChanged;
import io.taskmigo.project.ProjectException;
import io.taskmigo.project.ProjectService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.client.HttpClientErrorException;
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
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class ProjectApiIntegrationTest {

    @LocalServerPort
    int port;

    private final OrganizationService organizations;
    private final ProjectService projects;
    private final ProjectHistory history;

    ProjectApiIntegrationTest(OrganizationService organizations, ProjectService projects, ProjectHistory history) {
        this.organizations = organizations;
        this.projects = projects;
        this.history = history;
    }

    @Test
    void patchUpdatesOnlyProvidedFieldsAndRecordsHistory() {
        UUID organization = this.organizations.create("project-api-" + UUID.randomUUID(), "Project API");
        String oldKey = "before-" + UUID.randomUUID();
        String newKey = "after-" + UUID.randomUUID();
        UUID project = this.projects.create(organization, oldKey, "Before", "Before description");

        assertThat(
            this.api()
                .patch()
                .uri("/api/v0/projects/{projectId}", project)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "After"))
                .retrieve()
                .toEntity(String.class)
                .getBody()
        )
            .contains("\"success\":true")
            .contains("\"code\":\"resource.project.updated\"");

        Map<String, Object> clearDescription = new LinkedHashMap<>();
        clearDescription.put("description", null);
        this.api()
            .patch()
            .uri("/api/v0/projects/{projectId}", project)
            .contentType(MediaType.APPLICATION_JSON)
            .body(clearDescription)
            .retrieve()
            .toBodilessEntity();

        this.api()
            .patch()
            .uri("/api/v0/projects/{projectId}", project)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("key", newKey))
            .retrieve()
            .toBodilessEntity();

        this.api()
            .patch()
            .uri("/api/v0/projects/{projectId}", project)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of())
            .retrieve()
            .toBodilessEntity();

        var entries = this.history.list(project, null, 10).items();
        assertThat(entries)
            .extracting(ProjectHistory.Entry::action)
            .containsExactly(
                ProjectChanged.Action.PROJECT_UPDATED,
                ProjectChanged.Action.PROJECT_UPDATED,
                ProjectChanged.Action.PROJECT_UPDATED,
                ProjectChanged.Action.PROJECT_CREATED
            );
        assertThat(entries.get(0).changes()).containsExactly(new ProjectChanged.Change("key", oldKey, newKey));
        assertThat(entries.get(1).changes()).containsExactly(
            new ProjectChanged.Change("description", "Before description", null)
        );
        assertThat(entries.get(2).changes()).containsExactly(new ProjectChanged.Change("name", "Before", "After"));
        ProjectChanged.Target target = Objects.requireNonNull(entries.getFirst().target());
        assertThat(target.displayName()).isEqualTo("After");

        assertThat(this.projects.create(organization, oldKey, "Reused key", null)).isNotNull();
        assertThatThrownBy(() -> this.projects.create(organization, newKey, "Duplicate key", null))
            .isInstanceOf(ProjectException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void putIsNotSupportedForProjectUpdates() {
        UUID organization = this.organizations.create("no-put-" + UUID.randomUUID(), "No PUT");
        UUID project = this.projects.create(organization, "no-put-" + UUID.randomUUID(), "No PUT", null);

        assertThatThrownBy(() ->
            this.api()
                .put()
                .uri("/api/v0/projects/{projectId}", project)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("key", "replacement", "name", "Replacement"))
                .retrieve()
                .body(String.class)
        ).isInstanceOf(HttpClientErrorException.MethodNotAllowed.class);
    }

    @Test
    void patchRejectsUnsupportedFields() {
        UUID organization = this.organizations.create("patch-fields-" + UUID.randomUUID(), "Patch Fields");
        UUID project = this.projects.create(organization, "patch-fields-" + UUID.randomUUID(), "Patch Fields", null);

        assertThatThrownBy(() ->
            this.api()
                .patch()
                .uri("/api/v0/projects/{projectId}", project)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("organizationId", UUID.randomUUID()))
                .retrieve()
                .body(String.class)
        ).isInstanceOfSatisfying(HttpClientErrorException.BadRequest.class, exception ->
            assertThat(exception.getResponseBodyAsString())
                .contains("Unsupported Project fields")
                .contains("organizationId")
        );
    }

    @Test
    void archivedProjectsRejectPatches() {
        UUID organization = this.organizations.create(
            "archived-project-api-" + UUID.randomUUID(),
            "Archived Project API"
        );
        UUID project = this.projects.create(organization, "archived-" + UUID.randomUUID(), "Archived", null);
        this.projects.archive(project);

        assertThatThrownBy(() ->
            this.api()
                .patch()
                .uri("/api/v0/projects/{projectId}", project)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("name", "Still archived"))
                .retrieve()
                .body(String.class)
        ).isInstanceOfSatisfying(HttpClientErrorException.Conflict.class, exception ->
            assertThat(exception.getResponseBodyAsString())
                .contains("\"success\":false")
                .contains("\"status_code\":409")
                .contains("read-only")
        );
    }

    private RestClient api() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + this.port)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + this.accessToken())
            .build();
    }

    private String accessToken() {
        TokenResponse response = Objects.requireNonNull(
            this.http()
                .post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth("integration-client", "integration-secret"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&scope=taskmigo.api")
                .retrieve()
                .body(TokenResponse.class)
        );
        return response.access_token();
    }

    private RestClient http() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + this.port)
            .build();
    }

    private record TokenResponse(String access_token) {}
}
