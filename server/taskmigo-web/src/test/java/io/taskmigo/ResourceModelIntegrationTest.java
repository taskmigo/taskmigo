package io.taskmigo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.resource.PermissionCatalog;
import io.taskmigo.resource.ResourceException;
import io.taskmigo.resource.ResourceService;
import io.taskmigo.web.security.InternalClientReconciler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "taskmigo.security.internal-clients.cli.client-id=integration-client",
        "taskmigo.security.internal-clients.cli.client-secret=integration-secret",
        "taskmigo.security.signing-key-file=build/test-data/oauth-signing-key.pem",
    }
)
class ResourceModelIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.4-alpine");

    @Autowired
    ResourceService resources;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Flyway flyway;

    @Autowired
    RegisteredClientRepository clients;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    InternalClientReconciler internalClientReconciler;

    @LocalServerPort
    int port;

    @Test
    void flywayBuildsTheSchemaAndHibernateOnlyValidatesIt() {
        var current = Objects.requireNonNull(flyway.info().current());
        assertThat(current.getVersion().getVersion()).isEqualTo("2");
        assertThat(
            jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)
        ).isEqualTo(2);
        assertThat(
            jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'project_members'",
                Integer.class
            )
        ).isEqualTo(1);
        assertThat(
            jdbc.queryForObject(
                "select count(*) from oauth_client_management where registration_key = 'cli' and managed_by = 'SYSTEM'",
                Integer.class
            )
        ).isEqualTo(1);
        assertThat(
            jdbc.queryForObject(
                "select count(*) from oauth_service_principal_permissions where permission_key = 'system.resources.manage'",
                Integer.class
            )
        ).isEqualTo(1);
    }

    @Test
    void internalClientReconciliationIsIdempotent() {
        String registeredClientId = Objects.requireNonNull(
            jdbc.queryForObject(
                "select registered_client_id from oauth_client_management where registration_key = 'cli'",
                String.class
            )
        );
        String encodedSecret = Objects.requireNonNull(
            jdbc.queryForObject(
                "select client_secret from oauth2_registered_client where id = ?",
                String.class,
                registeredClientId
            )
        );

        internalClientReconciler.run(new DefaultApplicationArguments(new String[0]));

        assertThat(
            jdbc.queryForObject(
                "select registered_client_id from oauth_client_management where registration_key = 'cli'",
                String.class
            )
        ).isEqualTo(registeredClientId);
        assertThat(
            jdbc.queryForObject(
                "select client_secret from oauth2_registered_client where id = ?",
                String.class,
                registeredClientId
            )
        ).isEqualTo(encodedSecret);
        assertThat(
            jdbc.queryForObject(
                "select count(*) from oauth_service_principal_permissions where registered_client_id = ?",
                Integer.class,
                registeredClientId
            )
        ).isEqualTo(1);
    }

    @Test
    void flywayTriggerRejectsCrossOrganizationGroupMembershipEvenOutsideJpa() {
        UUID orgA = resources.createOrganization("db-org-a-" + UUID.randomUUID(), "DB A");
        UUID orgB = resources.createOrganization("db-org-b-" + UUID.randomUUID(), "DB B");
        UUID userB = resources.createUser(
            orgB,
            "db-user-b-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com",
            "DB User"
        );
        UUID groupA = resources.createGroup(orgA, "DB Group", null);
        assertThatThrownBy(() ->
            jdbc.update("insert into group_members(group_id, user_id) values (?, ?)", groupA, userB)
        ).hasMessageContaining("Group members must belong to the Group organization");
    }

    @Test
    void externalGroupAccessIsDerivedLiveAndAdditive() {
        UUID customer = resources.createOrganization("customer-" + UUID.randomUUID(), "Customer");
        UUID vendor = resources.createOrganization("vendor-" + UUID.randomUUID(), "Vendor");
        UUID engineer = resources.createUser(
            vendor,
            "engineer-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com",
            "Engineer"
        );
        UUID vendorGroup = resources.createGroup(vendor, "Backend", null);
        UUID project = resources.createProject(customer, "alpha-" + UUID.randomUUID(), "Alpha", null);
        UUID directRole = resources.createRole(customer, "Reader", null, Set.of(PermissionCatalog.PROJECT_READ));
        UUID groupRole = resources.createRole(
            customer,
            "Member manager",
            null,
            Set.of(PermissionCatalog.PROJECT_MEMBERS_MANAGE)
        );

        resources.addGroupMember(vendorGroup, engineer);
        UUID directMembership = resources.addProjectMember(project, "USER", engineer);
        resources.setProjectMemberRoles(project, directMembership, Set.of(directRole));
        UUID groupMembership = resources.addProjectMember(project, "GROUP", vendorGroup);
        resources.setProjectMemberRoles(project, groupMembership, Set.of(groupRole));

        assertThat(resources.effectivePermissions(project, engineer)).containsExactlyInAnyOrder(
            PermissionCatalog.PROJECT_READ,
            PermissionCatalog.PROJECT_MEMBERS_MANAGE
        );

        resources.removeGroupMember(vendorGroup, engineer);
        assertThat(resources.effectivePermissions(project, engineer)).containsExactly(PermissionCatalog.PROJECT_READ);
    }

    @Test
    void groupMembershipAndProjectRoleOrganizationInvariantsAreRejected() {
        UUID orgA = resources.createOrganization("org-a-" + UUID.randomUUID(), "A");
        UUID orgB = resources.createOrganization("org-b-" + UUID.randomUUID(), "B");
        UUID userB = resources.createUser(
            orgB,
            "user-b-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com",
            "B User"
        );
        UUID groupA = resources.createGroup(orgA, "A Group", null);
        assertThatThrownBy(() -> resources.addGroupMember(groupA, userB))
            .isInstanceOf(ResourceException.class)
            .hasMessageContaining("owning Organization");

        UUID projectA = resources.createProject(orgA, "p-" + UUID.randomUUID(), "Project", null);
        UUID member = resources.addProjectMember(projectA, "USER", userB);
        UUID roleB = resources.createRole(orgB, "Vendor Role", null, Set.of(PermissionCatalog.PROJECT_READ));
        assertThatThrownBy(() -> resources.setProjectMemberRoles(projectA, member, Set.of(roleB)))
            .isInstanceOf(ResourceException.class)
            .hasMessageContaining("Project Organization");
    }

    @Test
    void archivedProjectIsReadOnlyForMembershipMutations() {
        UUID org = resources.createOrganization("archive-" + UUID.randomUUID(), "Archive Org");
        UUID user = resources.createUser(
            org,
            "archive-user-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com",
            "Archive User"
        );
        UUID project = resources.createProject(org, "archive-project-" + UUID.randomUUID(), "Archive Project", null);
        resources.archiveProject(project);
        assertThatThrownBy(() -> resources.addProjectMember(project, "USER", user))
            .isInstanceOf(ResourceException.class)
            .hasMessageContaining("read-only");
    }

    @Test
    void authorizationServerIssuesClientCredentialsTokenThatProtectsApi() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String basic = Base64.getEncoder().encodeToString(
            "integration-client:integration-secret".getBytes(StandardCharsets.UTF_8)
        );
        HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/token"))
            .header("Authorization", "Basic " + basic)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope=taskmigo.api"))
            .build();
        HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(tokenResponse.statusCode()).isEqualTo(200);
        String token = extractAccessToken(tokenResponse.body());

        HttpRequest apiRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v0/permissions"))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
        HttpResponse<String> apiResponse = client.send(apiRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(apiResponse.statusCode()).isEqualTo(200);
        assertThat(apiResponse.body()).contains(PermissionCatalog.PROJECT_READ);
        assertThat(
            jdbc.queryForObject(
                "select count(*) from oauth2_authorization where principal_name = 'integration-client'",
                Integer.class
            )
        ).isGreaterThanOrEqualTo(1);
    }

    @Test
    void apiRejectsClientThatHasScopeWithoutServicePrincipalPermission() throws Exception {
        String clientId = "scope-only-" + UUID.randomUUID();
        String clientSecret = "scope-only-secret";
        clients.save(
            RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode(clientSecret))
                .clientName("Scope only")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("taskmigo.api")
                .build()
        );

        HttpClient client = HttpClient.newHttpClient();
        String basic = Base64.getEncoder().encodeToString(
            (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)
        );
        HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/oauth2/token"))
            .header("Authorization", "Basic " + basic)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope=taskmigo.api"))
            .build();
        HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(tokenResponse.statusCode()).isEqualTo(200);

        HttpRequest apiRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v0/permissions"))
            .header("Authorization", "Bearer " + extractAccessToken(tokenResponse.body()))
            .GET()
            .build();
        assertThat(client.send(apiRequest, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }

    @Test
    void apiReferenceRendersTheGeneratedOpenApiDocument() throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

        HttpRequest openApiRequest = HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/api/docs/openapi.json")
        )
            .GET()
            .build();
        HttpResponse<String> openApiResponse = client.send(openApiRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(openApiResponse.statusCode()).isEqualTo(200);
        assertThat(openApiResponse.body())
            .contains("\"title\":\"Taskmigo API\"")
            .contains("\"taskmigoOAuth\"")
            .contains("\"/api/v0/permissions\"");

        HttpRequest apiDocsRequest = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/docs"))
            .GET()
            .build();
        HttpResponse<String> apiDocsResponse = client.send(apiDocsRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(apiDocsResponse.statusCode()).isEqualTo(200);
        assertThat(apiDocsResponse.body()).contains("Taskmigo API Reference").contains("/api/docs/openapi.json");
    }

    private static String extractAccessToken(String body) {
        var matcher = Pattern.compile("\\\"access_token\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(body);
        assertThat(matcher.find()).as("token response contains access_token: %s", body).isTrue();
        return matcher.group(1);
    }
}
