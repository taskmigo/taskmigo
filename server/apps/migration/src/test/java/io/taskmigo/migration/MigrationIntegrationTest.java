package io.taskmigo.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.authorization.provisioning.AuthorizationProvisioningService;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.identity.provisioning.IdentityProvisioningService;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserService;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
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
        "TASKMIGO_SYSTEM_PASSWORD_HASH={noop}integration-password",
        "TASKMIGO_MACHINE_CLIENT_ID=integration-client",
        "TASKMIGO_MACHINE_CLIENT_SECRET_HASH={noop}integration-secret",
        "TASKMIGO_AUTH_CLIENT_SECRET_HASH={noop}browser-integration-secret",
        "TASKMIGO_CLIENT_URL=http://localhost:3000",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MigrationIntegrationTest {

    private final Flyway flyway;
    private final JdbcRegisteredClientRepository clients;
    private final InternalClientReconciler internalClients;
    private final MigrationResourceLoader resources;
    private final PasswordEncoder passwordEncoder;
    private final UserService users;
    private final RoleService roles;
    private final StatementService statements;
    private final AuthorizationProvisioningService authorizationProvisioning;
    private final IdentityProvisioningService identityProvisioning;

    MigrationIntegrationTest(
        Flyway flyway,
        JdbcRegisteredClientRepository clients,
        InternalClientReconciler internalClients,
        MigrationResourceLoader resources,
        PasswordEncoder passwordEncoder,
        UserService users,
        RoleService roles,
        StatementService statements,
        AuthorizationProvisioningService authorizationProvisioning,
        IdentityProvisioningService identityProvisioning
    ) {
        this.flyway = flyway;
        this.clients = clients;
        this.internalClients = internalClients;
        this.resources = resources;
        this.passwordEncoder = passwordEncoder;
        this.users = users;
        this.roles = roles;
        this.statements = statements;
        this.authorizationProvisioning = authorizationProvisioning;
        this.identityProvisioning = identityProvisioning;
    }

    /**
     * Verifies: migration applies the canonical schema and managed clients.
     * Given: a fresh Testcontainers database and flat YAML resources.
     * Expect: version 1, the encoded system password, and both internal clients exist.
     */
    @Test
    @DisplayName("installs the schema, system user, and managed OAuth clients")
    void shouldInstallRequiredStateWhenMigrationRuns() {
        var migrations = this.flyway.info().applied();
        assertThat(migrations).hasSize(1);
        assertThat(migrations[0].getVersion().getVersion()).isEqualTo("1");

        var system = this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow();
        assertThat(
            this.passwordEncoder.matches("integration-password", Objects.requireNonNull(system.passwordHash()))
        ).isTrue();

        RegisteredClient internal = this.storedClient("integration-client");
        assertThat(InternalClientMetadata.isManaged(internal)).isTrue();
        assertThat(internal.getAuthorizationGrantTypes()).containsExactly(AuthorizationGrantType.CLIENT_CREDENTIALS);
        assertThat(internal.getScopes()).isEmpty();

        RegisteredClient browser = this.storedClient("taskmigo-client");
        assertThat(InternalClientMetadata.isManaged(browser)).isTrue();
        assertThat(browser.getClientAuthenticationMethods()).containsExactly(
            ClientAuthenticationMethod.CLIENT_SECRET_BASIC
        );
        assertThat(browser.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
            AuthorizationGrantType.AUTHORIZATION_CODE,
            AuthorizationGrantType.REFRESH_TOKEN
        );
        assertThat(browser.getRedirectUris()).containsExactly("http://localhost:3000/api/auth/callback");
        assertThat(browser.getPostLogoutRedirectUris()).containsExactly("http://localhost:3000/");
        assertThat(browser.getScopes()).containsExactlyInAnyOrder(OidcScopes.OPENID, OidcScopes.PROFILE);
        assertThat(browser.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(browser.getClientSettings().isRequireAuthorizationConsent()).isFalse();
        assertThat(browser.getTokenSettings().isReuseRefreshTokens()).isFalse();
    }

    /**
     * Verifies: the flat resource loader binds OAuth clients to Spring's native property class.
     * Given: security.yaml with a registration map and placeholder values.
     * Expect: the resolved native Client contains no implicit API scope.
     */
    @Test
    @DisplayName("binds flat security YAML to native Spring OAuth client properties")
    void shouldBindNativeClientPropertiesWhenSecurityYamlIsLoaded() {
        Client machine = Objects.requireNonNull(this.resources.load().clients().get("cli"));

        assertThat(machine.getRegistration().getClientId()).isEqualTo("integration-client");
        assertThat(machine.getRegistration().getClientSecret()).isEqualTo("{noop}integration-secret");
        assertThat(machine.getRegistration().getScopes()).isEmpty();
    }

    /**
     * Verifies: re-running the internal client reconciliation is idempotent.
     * Given: already-provisioned managed clients and their pre-encoded secret.
     * Expect: database identifiers and the system password hash remain unchanged.
     */
    @Test
    @DisplayName("preserves managed client and user state during reconciliation")
    void shouldPreserveStateWhenReconciliationRunsAgain() {
        String internalId = this.storedClient("integration-client").getId();
        String browserId = this.storedClient("taskmigo-client").getId();
        String passwordHash = Objects.requireNonNull(
            this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().passwordHash()
        );

        this.internalClients.reconcile(this.resources.load().clients());

        assertThat(this.storedClient("integration-client").getId()).isEqualTo(internalId);
        assertThat(this.storedClient("taskmigo-client").getId()).isEqualTo(browserId);
        assertThat(this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().passwordHash()).isEqualTo(
            passwordHash
        );
    }

    /**
     * Verifies: configured statements and roles are reconciled using their canonical codes.
     * Given: the built-in flat authorization resources.
     * Expect: statement code and role code/display name are persisted and assigned to system.
     */
    @Test
    @DisplayName("reconciles built-in statements through normal role assignments")
    void shouldAssignBuiltInStatementsWhenMigrationRuns() {
        var system = this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow();

        var persistedStatements = this.statements.list(1, 100).items();
        var persistedRoles = this.roles.effectiveRoles(this.users.roleIds(system.id()));

        assertThat(persistedStatements).extracting(StatementInfo::code).contains("system_operator_request_all");
        assertThat(persistedRoles).extracting(RoleInfo::code).contains("system-operator");
        assertThat(persistedRoles)
            .filteredOn(role -> role.code().equals("system-operator"))
            .extracting(RoleInfo::displayName)
            .contains("System Operator");
        assertThat(this.users.roleIds(system.id())).hasSize(1);
    }

    /**
     * Verifies: embedded policies and scopes survive YAML provisioning.
     * Given: every built-in statement in statements.yaml.
     * Expect: each statement has its declared scope and a non-empty executable policy.
     */
    @Test
    @DisplayName("persists Embedded Language policies for every built-in statement")
    void shouldPersistEmbeddedLanguagePoliciesWhenMigrationRuns() {
        Map<String, Scope> builtInScopes = Map.of(
            "system_operator_request_all",
            Scope.REQUEST,
            "system_operator_object_all",
            Scope.OBJECT,
            "administrator_request_all",
            Scope.REQUEST,
            "administrator_object_all",
            Scope.OBJECT,
            "administrator_hide_system_user",
            Scope.OBJECT
        );

        var persistedStatements = this.statements.list(1, 100).items();

        assertThat(persistedStatements)
            .filteredOn(statement -> builtInScopes.containsKey(statement.code()))
            .hasSize(builtInScopes.size())
            .allSatisfy(statement -> {
                assertThat(statement.scope()).isEqualTo(builtInScopes.get(statement.code()));
                assertThat(statement.policy()).isNotBlank().startsWith("return");
            });
    }

    /**
     * Verifies: identity reconciliation replaces profile and relationship state without re-encoding a supplied hash.
     * Given: a user with a pre-encoded password, a role, and no direct statement field.
     * Expect: the same user id is updated and the original system password remains unchanged.
     */
    @Test
    @DisplayName("upserts users from migration data")
    void shouldUpsertManagedUserWhenUserIsMissingOrPresent() {
        String username = "migration-user";
        UUID roleId = this.authorizationProvisioning.requireRole("system-operator");
        UUID createdId = this.identityProvisioning.reconcileUser(
            username,
            "{noop}migration-password",
            Set.of("MIGRATION@EXAMPLE.COM"),
            "Migration",
            "User",
            Set.of(roleId),
            Set.of()
        );
        String passwordHash = Objects.requireNonNull(
            this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().passwordHash()
        );

        UUID reconciledId = this.identityProvisioning.reconcileUser(
            username,
            null,
            Set.of("updated@example.com"),
            "Updated",
            "User",
            Set.of(roleId),
            Set.of()
        );

        assertThat(reconciledId).isEqualTo(createdId);
        assertThat(this.users.require(createdId))
            .extracting(UserInfo::firstName, UserInfo::emails)
            .containsExactly("Updated", Set.of("updated@example.com"));
        assertThat(this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().passwordHash()).isEqualTo(
            passwordHash
        );
    }

    /**
     * Verifies: serializable reconciliation converges concurrent attempts to one registered client.
     * Given: two submitted reconciliation tasks for the same registration.
     * Expect: one managed client remains and no duplicate client id is created.
     */
    @Test
    @DisplayName("creates one registration during concurrent internal client reconciliation")
    void shouldCreateOneRegistrationWhenInternalClientReconciliationIsConcurrent() throws Exception {
        String clientId = "concurrent-" + UUID.randomUUID();
        var configuredClients = Map.of("concurrent", client(clientId, "{noop}concurrent-secret"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> this.internalClients.reconcile(configuredClients));
            var second = executor.submit(() -> this.internalClients.reconcile(configuredClients));
            first.get();
            second.get();
        }

        assertThat(this.storedClient(clientId).getId()).isEqualTo("concurrent");
    }

    /**
     * Verifies: migration refuses to adopt a client without the internal ownership marker.
     * Given: an existing user-owned registration with the same client id.
     * Expect: reconciliation fails closed and leaves that registration unmanaged.
     */
    @Test
    @DisplayName("refuses to adopt an unmanaged internal OAuth client")
    void shouldRejectClientWhenInternalClientIsUnmanaged() {
        String clientId = "unmanaged-" + UUID.randomUUID();
        this.clients.save(
            RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(this.passwordEncoder.encode("secret"))
                .clientName("Unmanaged")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build()
        );

        assertThatThrownBy(() ->
            this.internalClients.reconcile(Map.of("unmanaged", client(clientId, "{noop}secret")))
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
        registration.setScopes(new LinkedHashSet<>());
        return client;
    }
}
