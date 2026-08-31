package io.taskmigo.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.access.AccessService;
import io.taskmigo.acl.AclPolicyRegistry;
import io.taskmigo.acl.AclStatement;
import io.taskmigo.acl.ApiAclEngine;
import io.taskmigo.identity.oauth.InternalClientMetadata;
import io.taskmigo.organization.OrganizationService;
import io.taskmigo.user.SystemUser;
import io.taskmigo.user.UserService;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.test.context.TestConstructor;

@SpringBootTest(
    properties = {
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-id=integration-client",
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-secret=integration-secret",
        "spring.security.oauth2.authorizationserver.client.cli.registration.client-authentication-methods=client_secret_basic",
        "spring.security.oauth2.authorizationserver.client.cli.registration.authorization-grant-types=client_credentials",
        "spring.security.oauth2.authorizationserver.client.cli.registration.scopes=taskmigo.api",
        "taskmigo.security.bootstrap-user.password=integration-password",
        "taskmigo.security.browser-authentication.enabled=true",
        "taskmigo.security.browser-authentication.client-secret=browser-integration-secret",
        "taskmigo.security.browser-authentication.client-url=http://localhost:3000",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BootstrapIntegrationTest {

    private final Flyway flyway;
    private final JdbcRegisteredClientRepository clients;
    private final InternalClientReconciler internalClients;
    private final BrowserClientReconciler browserClient;
    private final SystemUserReconciler systemUser;
    private final SystemAclPolicyReconciler systemAcl;
    private final SystemAccessReconciler systemAccess;
    private final AclPolicyRegistry policies;
    private final AccessService access;
    private final ApiAclEngine engine;
    private final PasswordEncoder passwordEncoder;
    private final UserService users;
    private final OrganizationService organizations;

    BootstrapIntegrationTest(
        Flyway flyway,
        JdbcRegisteredClientRepository clients,
        InternalClientReconciler internalClients,
        BrowserClientReconciler browserClient,
        SystemUserReconciler systemUser,
        SystemAclPolicyReconciler systemAcl,
        SystemAccessReconciler systemAccess,
        AclPolicyRegistry policies,
        AccessService access,
        ApiAclEngine engine,
        PasswordEncoder passwordEncoder,
        UserService users,
        OrganizationService organizations
    ) {
        this.flyway = flyway;
        this.clients = clients;
        this.internalClients = internalClients;
        this.browserClient = browserClient;
        this.systemUser = systemUser;
        this.systemAcl = systemAcl;
        this.systemAccess = systemAccess;
        this.policies = policies;
        this.access = access;
        this.engine = engine;
        this.passwordEncoder = passwordEncoder;
        this.users = users;
        this.organizations = organizations;
    }

    @Test
    void bootstrapOwnsMigrationAndInstallationState() {
        var migrations = this.flyway.info().applied();
        assertThat(migrations).hasSize(1);
        assertThat(migrations[0].getVersion().getVersion()).isEqualTo("1");

        var system = this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow();
        assertThat(
            this.passwordEncoder.matches("integration-password", Objects.requireNonNull(system.passwordHash()))
        ).isTrue();

        var policySnapshot = this.policies.snapshot(null);
        assertThat(policySnapshot.requestPolicies())
            .extracting(policy -> policy.name())
            .containsExactly("system/api-authenticated");
        assertThat(policySnapshot.responsePolicies())
            .extracting(policy -> policy.name())
            .containsExactly("system/project-organization-boundary");

        UUID organizationId = this.organizations.create("access-" + UUID.randomUUID(), "Access Org");
        String createProjectPath = "/api/v0/organizations/" + organizationId + "/projects";
        AclStatement createProject = this.access
            .statementCatalog(organizationId)
            .stream()
            .filter(statement -> statement.mode() == AclStatement.Mode.REQUEST)
            .filter(statement -> statement.target().matches("POST", createProjectPath))
            .findFirst()
            .orElseThrow();
        var createProjectInfo = this.access
            .statements(organizationId)
            .stream()
            .filter(statement -> createProject.key().equals(statement.key()))
            .findFirst()
            .orElseThrow();
        var projectManager = this.access
            .roles(organizationId)
            .stream()
            .filter(role -> "SYSTEM".equals(role.origin()))
            .filter(role -> role.statementIds().contains(createProjectInfo.id()))
            .findFirst()
            .orElseThrow();
        assertThat(projectManager.statementIds()).contains(createProjectInfo.id());

        UUID customRole = this.access.createRole(
            organizationId,
            "delivery-lead",
            "Delivery Lead",
            "Custom Role reusing a built-in Statement",
            Set.of(createProjectInfo.id())
        );
        UUID userId = this.users.create(
            organizationId,
            "statement-user-" + UUID.randomUUID(),
            Set.of(),
            "Statement",
            "User"
        );
        this.access.setUserRoles(userId, Set.of(customRole));
        Set<String> effectiveStatements = this.access.effectiveStatementKeys(userId);
        assertThat(effectiveStatements).containsExactly(createProject.key());

        Map<String, Object> requestContext = Map.of(
            "principal.id",
            userId,
            "principal.organizationId",
            organizationId,
            "request.organizationId",
            organizationId
        );
        assertThat(
            this.engine.isRequestAllowed(
                policySnapshot.requestPolicies(),
                this.access.statementCatalog(organizationId),
                effectiveStatements,
                false,
                "POST",
                createProjectPath,
                requestContext
            )
        ).isTrue();
        assertThat(
            this.engine.isRequestAllowed(
                policySnapshot.requestPolicies(),
                this.access.statementCatalog(organizationId),
                Set.of(),
                false,
                "POST",
                createProjectPath,
                requestContext
            )
        ).isFalse();
        assertThat(
            this.engine.isRequestAllowed(
                policySnapshot.requestPolicies(),
                this.access.statementCatalog(organizationId),
                effectiveStatements,
                false,
                "POST",
                createProjectPath,
                Map.of(
                    "principal.id",
                    userId,
                    "principal.organizationId",
                    UUID.randomUUID(),
                    "request.organizationId",
                    organizationId
                )
            )
        ).isFalse();

        RegisteredClient internal = this.storedClient("integration-client");
        assertThat(InternalClientMetadata.isManaged(internal)).isTrue();
        assertThat(internal.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);

        RegisteredClient browser = this.storedClient(BrowserClientMetadata.CLIENT_ID);
        assertThat(BrowserClientMetadata.isManaged(browser)).isTrue();
        assertThat(browser.getClientAuthenticationMethods()).containsExactly(
            ClientAuthenticationMethod.CLIENT_SECRET_BASIC
        );
        assertThat(browser.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
            AuthorizationGrantType.AUTHORIZATION_CODE,
            AuthorizationGrantType.REFRESH_TOKEN
        );
        assertThat(browser.getRedirectUris()).containsExactly("http://localhost:3000/api/auth/callback");
        assertThat(browser.getPostLogoutRedirectUris()).containsExactly("http://localhost:3000/");
        assertThat(browser.getScopes()).containsExactlyInAnyOrder(
            OidcScopes.OPENID,
            OidcScopes.PROFILE,
            InternalClientMetadata.API_SCOPE
        );
        assertThat(browser.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(browser.getClientSettings().isRequireAuthorizationConsent()).isFalse();
        assertThat(browser.getTokenSettings().isReuseRefreshTokens()).isFalse();
    }

    @Test
    void reconciliationIsIdempotent() throws Exception {
        String internalId = this.storedClient("integration-client").getId();
        String browserId = this.storedClient(BrowserClientMetadata.CLIENT_ID).getId();
        String passwordHash = Objects.requireNonNull(
            this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().passwordHash()
        );
        UUID organizationId = this.organizations.create("reconcile-" + UUID.randomUUID(), "Reconcile");
        Map<String, UUID> statementIds = this.access
            .statements(organizationId)
            .stream()
            .filter(statement -> "SYSTEM".equals(statement.origin()))
            .collect(
                java.util.stream.Collectors.toMap(AccessService.StatementInfo::key, AccessService.StatementInfo::id)
            );
        Map<String, UUID> roleIds = this.access
            .roles(organizationId)
            .stream()
            .filter(role -> "SYSTEM".equals(role.origin()))
            .collect(java.util.stream.Collectors.toMap(AccessService.RoleInfo::key, AccessService.RoleInfo::id));

        this.internalClients.reconcile(Map.of("cli", client("integration-client", "integration-secret")));
        this.browserClient.reconcile();
        this.systemUser.reconcile();
        this.systemAcl.reconcile();
        this.systemAccess.reconcile();

        assertThat(this.storedClient("integration-client").getId()).isEqualTo(internalId);
        assertThat(this.storedClient(BrowserClientMetadata.CLIENT_ID).getId()).isEqualTo(browserId);
        assertThat(this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().passwordHash()).isEqualTo(
            passwordHash
        );
        assertThat(
            this.access
                .statements(organizationId)
                .stream()
                .filter(statement -> "SYSTEM".equals(statement.origin()))
                .collect(
                    java.util.stream.Collectors.toMap(AccessService.StatementInfo::key, AccessService.StatementInfo::id)
                )
        ).isEqualTo(statementIds);
        assertThat(
            this.access
                .roles(organizationId)
                .stream()
                .filter(role -> "SYSTEM".equals(role.origin()))
                .collect(java.util.stream.Collectors.toMap(AccessService.RoleInfo::key, AccessService.RoleInfo::id))
        ).isEqualTo(roleIds);
        assertThat(this.policies.snapshot(null).requestPolicies()).hasSize(1);
        assertThat(this.policies.snapshot(null).responsePolicies()).hasSize(1);
    }

    @Test
    void concurrentInternalClientReconciliationCreatesOneRegistration() throws Exception {
        String clientId = "concurrent-" + UUID.randomUUID();
        var configuredClients = Map.of("concurrent", client(clientId, "concurrent-secret"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> this.internalClients.reconcile(configuredClients));
            var second = executor.submit(() -> this.internalClients.reconcile(configuredClients));
            first.get();
            second.get();
        }

        assertThat(this.storedClient(clientId).getId()).isEqualTo("concurrent");
    }

    @Test
    void internalClientReconciliationRefusesToAdoptUnmanagedClient() {
        String clientId = "unmanaged-" + UUID.randomUUID();
        this.clients.save(
            RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(this.passwordEncoder.encode("secret"))
                .clientName("Unmanaged")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope(InternalClientMetadata.API_SCOPE)
                .build()
        );

        assertThatThrownBy(() ->
            this.internalClients.reconcile(Map.of("unmanaged", client(clientId, "secret")))
        ).hasMessageContaining("Refusing to adopt unmanaged OAuth client");
    }

    private RegisteredClient storedClient(String clientId) {
        return Objects.requireNonNull(this.clients.findByClientId(clientId));
    }

    private static Client client(String clientId, String clientSecret) {
        Client client = new Client();
        var registration = client.getRegistration();
        registration.setClientId(clientId);
        registration.setClientSecret(clientSecret);
        registration.setClientAuthenticationMethods(new LinkedHashSet<>(Set.of("client_secret_basic")));
        registration.setAuthorizationGrantTypes(new LinkedHashSet<>(Set.of("client_credentials")));
        registration.setScopes(new LinkedHashSet<>(Set.of(InternalClientMetadata.API_SCOPE)));
        return client;
    }
}
