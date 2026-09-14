package io.taskmigo.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.identity.authorization.role.RoleService;
import io.taskmigo.identity.authorization.statement.StatementService;
import io.taskmigo.identity.oauth.RegisteredClientDefinition;
import io.taskmigo.identity.oauth.RegisteredClientRepository;
import io.taskmigo.identity.oauth.RegisteredClientType;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.UserService;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.test.context.TestConstructor;

@SpringBootTest(
    properties = {
        "TASKMIGO_SYSTEM_PASSWORD={bcrypt}$2a$10$06C0Iz4cZk50m6RPxwd5EOshPgze.x4RV7xU2b7gubrUnzMNnOOmW",
        "taskmigo.registered-clients.cli.registration.client-id=integration-client",
        "taskmigo.registered-clients.cli.registration.client-secret=integration-secret",
        "taskmigo.registered-clients.cli.registration.client-authentication-methods=client_secret_basic",
        "taskmigo.registered-clients.cli.registration.authorization-grant-types=client_credentials",
        "taskmigo.registered-clients.browser.registration.client-secret=browser-integration-secret",
        "taskmigo.registered-clients.browser.registration.redirect-uris[0]=http://localhost:3000/api/auth/callback",
        "taskmigo.registered-clients.browser.registration.post-logout-redirect-uris[0]=http://localhost:3000/",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MigrationIntegrationTest {

    private static final String INTEGRATION_PASSWORD_HASH =
        "{bcrypt}$2a$10$06C0Iz4cZk50m6RPxwd5EOshPgze.x4RV7xU2b7gubrUnzMNnOOmW";

    private final Flyway flyway;
    private final MigrationProperties properties;
    private final RegisteredClientRepository clients;
    private final RegisteredClientReconciler clientReconciler;
    private final PasswordEncoder passwordEncoder;
    private final UserService users;
    private final RoleService access;
    private final StatementService statements;

    MigrationIntegrationTest(
        Flyway flyway,
        MigrationProperties properties,
        RegisteredClientRepository clients,
        RegisteredClientReconciler clientReconciler,
        PasswordEncoder passwordEncoder,
        UserService users,
        RoleService access,
        StatementService statements
    ) {
        this.flyway = flyway;
        this.properties = properties;
        this.clients = clients;
        this.clientReconciler = clientReconciler;
        this.passwordEncoder = passwordEncoder;
        this.users = users;
        this.access = access;
        this.statements = statements;
    }

    /**
     * Verifies that migration applies the schema, configured system user, and both browser and machine clients.
     *
     * Given: a migration configuration containing a complete system user and two registered clients.
     * Expect: the schema is migrated, the configured user profile and assignments are persisted, and both clients
     * have their expected OAuth settings.
     */
    @Test
    @DisplayName("installs configured migration state")
    void shouldInstallConfiguredStateWhenMigrationRuns() {
        // Arrange
        var migrations = this.flyway.info().applied();

        // Act
        var system = this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow();
        RegisteredClientDefinition internal = this.storedDefinition("integration-client");
        RegisteredClient browser = this.storedClient(BrowserClientMetadata.CLIENT_ID);

        // Assert
        assertThat(migrations).hasSize(1);
        assertThat(migrations[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(
            this.passwordEncoder.matches("integration-password", Objects.requireNonNull(system.passwordHash()))
        ).isTrue();
        assertThat(system.passwordHash()).isEqualTo(INTEGRATION_PASSWORD_HASH);
        assertThat(this.users.require(system.id()))
            .extracting(UserInfo::firstName, UserInfo::lastName, UserInfo::emails)
            .containsExactly("System", "User", Set.of("system@example.com"));
        assertThat(this.users.findForAuthentication("admin")).isPresent();
        assertThat(this.users.roleIds(system.id())).hasSize(1);
        assertThat(internal.type()).isEqualTo(RegisteredClientType.INTERNAL);
        assertThat(internal.registeredClient().getAuthorizationGrantTypes()).containsExactly(
            AuthorizationGrantType.CLIENT_CREDENTIALS
        );
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

    ///
    /// Verifies that migration binds registered-client fields through Spring Boot's authorization-server properties.
    ///
    /// Given: the configured internal client and no explicit values for its optional client settings.
    /// Expect: the bound value is Spring's client properties type and its documented defaults are available.
    ///
    @Test
    @DisplayName("binds migration clients through Spring authorization server properties")
    void shouldBindRegisteredClientWhenMigrationPropertiesAreConfigured() {
        // Arrange
        var client = Objects.requireNonNull(this.properties.registeredClients().get("cli"));

        // Act
        var registration = client.getRegistration();

        // Assert
        assertThat(client).isInstanceOf(OAuth2AuthorizationServerProperties.Client.class);
        assertThat(client.isAbsent()).isFalse();
        assertThat(registration.getClientId()).isEqualTo("integration-client");
        assertThat(client.getType()).isEqualTo(RegisteredClientType.INTERNAL);
        assertThat(client.isRequireProofKey()).isTrue();
        assertThat(client.getToken().getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(5));
    }

    /**
     * Verifies that a changed configured password replaces the system credential while preserving the user identity.
     *
     * Given: the persisted system user and a new migration password hash.
     * Expect: the same user id remains and the new password authenticates.
     */
    @Test
    @DisplayName("updates the system password when migration configuration changes")
    void shouldUpdateSystemPasswordWhenMigrationConfigurationChanges() {
        // Arrange
        var system = this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow();
        UUID systemId = system.id();
        String rotatedPassword = "rotated-integration-password";

        // Act
        this.users.reconcileManagedUser(
            SystemUser.USERNAME,
            Set.of("system@example.com"),
            "System",
            "User",
            Set.of(this.access.requireRoleByName("System Operator")),
            Set.of(this.statements.requireByName("system_operator_request_all")),
            this.passwordEncoder.encode(rotatedPassword)
        );
        var rotated = this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow();

        // Assert
        assertThat(rotated.id()).isEqualTo(systemId);
        assertThat(
            this.passwordEncoder.matches(rotatedPassword, Objects.requireNonNull(rotated.passwordHash()))
        ).isTrue();

        // Restore the configured source-of-truth so this shared integration context remains order-independent.
        this.users.reconcileManagedUser(
            SystemUser.USERNAME,
            Set.of("system@example.com"),
            "System",
            "User",
            Set.of(this.access.requireRoleByName("System Operator")),
            Set.of(this.statements.requireByName("system_operator_request_all")),
            INTEGRATION_PASSWORD_HASH
        );
    }

    /**
     * Verifies that rerunning migration is idempotent for managed users and clients.
     *
     * Given: the same complete migration configuration is reconciled again.
     * Expect: registered-client ids and the system user id remain unchanged.
     */
    @Test
    @DisplayName("preserves managed identities during repeated migration")
    void shouldPreserveManagedIdentitiesWhenMigrationRunsAgain() {
        // Arrange
        String internalId = this.storedClient("integration-client").getId();
        String browserId = this.storedClient(BrowserClientMetadata.CLIENT_ID).getId();
        UUID systemId = this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().id();

        // Act
        this.clientReconciler.reconcile(this.properties.registeredClients());

        // Assert
        assertThat(this.storedClient("integration-client").getId()).isEqualTo(internalId);
        assertThat(this.storedClient(BrowserClientMetadata.CLIENT_ID).getId()).isEqualTo(browserId);
        assertThat(this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().id()).isEqualTo(systemId);
    }

    /**
     * Verifies that migration authorization data uses the canonical Statement model and normal Role assignment path.
     *
     * Given: migration has reconciled the authorization catalog and configured system user.
     * Expect: the canonical statement and managed system role are persisted and assigned.
     */
    @Test
    @DisplayName("reconciles built-in statements through normal role assignments")
    void shouldAssignBuiltInStatementsWhenMigrationRuns() {
        // Arrange
        var system = this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow();

        // Act
        var persistedStatements = this.statements.list(1, 100).items();
        var roles = this.access.effectiveRoles(this.users.roleIds(system.id()));

        // Assert
        assertThat(persistedStatements).extracting(StatementInfo::name).contains("system_operator_request_all");
        assertThat(roles).extracting(RoleInfo::name).contains("System Operator");
        assertThat(this.users.roleIds(system.id())).hasSize(1);
    }

    /**
     * Verifies that every managed migration Statement uses the final Embedded Language contract.
     *
     * Given: the five Statements declared in the managed migration authorization bundle.
     * Expect: every definition is persisted with a canonical scope and a non-blank direct-body policy.
     */
    @Test
    @DisplayName("persists Embedded Language policies for every built-in statement")
    void shouldPersistEmbeddedLanguagePoliciesWhenMigrationRuns() {
        // Arrange
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

        // Act
        var persistedStatements = this.statements.list(1, 100).items();

        // Assert
        assertThat(persistedStatements)
            .filteredOn(statement -> builtInScopes.containsKey(statement.name()))
            .hasSize(builtInScopes.size())
            .allSatisfy(statement -> {
                assertThat(statement.scope()).isEqualTo(builtInScopes.get(statement.name()));
                assertThat(statement.policy()).isNotBlank().startsWith("return");
            });
    }

    /**
     * Verifies that concurrent reconciliation creates one persisted registration for the same internal client.
     *
     * Given: two concurrent reconciliation calls with one internal client identifier.
     * Expect: one registration id is stored and both calls complete successfully.
     */
    @Test
    @DisplayName("creates one registration during concurrent internal client reconciliation")
    void shouldCreateOneRegistrationWhenInternalClientReconciliationIsConcurrent() throws Exception {
        // Arrange
        String clientId = "concurrent-" + UUID.randomUUID();
        var configuredClients = Map.of("concurrent", client(clientId, "concurrent-secret"));

        // Act
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> this.clientReconciler.reconcile(configuredClients));
            var second = executor.submit(() -> this.clientReconciler.reconcile(configuredClients));
            first.get();
            second.get();
        }

        // Assert
        assertThat(this.storedClient(clientId).getId()).isEqualTo("concurrent");
    }

    /**
     * Verifies that an internal client may use an ordinary client identifier.
     *
     * Given: an internal machine client without a reserved identifier prefix.
     * Expect: migration persists the client because ownership is represented by its classification.
     */
    @Test
    @DisplayName("accepts internal clients without a reserved prefix")
    void shouldAcceptInternalClientWhenClientIdHasNoReservedPrefix() {
        // Arrange
        String clientId = "ordinary-internal-" + UUID.randomUUID();

        // Act
        this.clientReconciler.reconcile(Map.of("ordinary", client(clientId, "secret")));

        // Assert
        assertThat(this.storedDefinition(clientId).type()).isEqualTo(RegisteredClientType.INTERNAL);
    }

    /**
     * Verifies that user-created clients use the same reconciliation contract with a distinct persisted type.
     *
     * Given: a user client definition with an ordinary client identifier.
     * Expect: migration persists it as a user client without requiring an internal naming convention.
     */
    @Test
    @DisplayName("reconciles user-created clients with their persisted type")
    void shouldReconcileUserClientWhenTypeIsUser() {
        // Arrange
        String clientId = "user-client-" + UUID.randomUUID();
        var definition = client(clientId, "user-secret");
        definition.setType(RegisteredClientType.USER);

        // Act
        this.clientReconciler.reconcile(Map.of("user", definition));

        // Assert
        assertThat(this.storedDefinition(clientId).type()).isEqualTo(RegisteredClientType.USER);
    }

    /**
     * Verifies that duplicate configured client identifiers are rejected before reconciliation begins.
     *
     * Given: two registration ids that resolve to one machine client identifier.
     * Expect: migration reports the duplicate and does not persist either registration.
     */
    @Test
    @DisplayName("rejects duplicate registered client identifiers")
    void shouldRejectDuplicateClientIdsBeforePersistence() {
        // Arrange
        String clientId = "duplicate-" + UUID.randomUUID();
        var configuredClients = Map.of(
            "first",
            client(clientId, "first-secret"),
            "second",
            client(clientId, "second-secret")
        );

        // Act + Assert
        assertThatThrownBy(() -> this.clientReconciler.reconcile(configuredClients)).hasMessageContaining(
            "Duplicate registered client-id: " + clientId
        );
        assertThat(this.clients.findByClientId(clientId)).isNull();
    }

    /**
     * Verifies that migration cannot change a client classification implicitly.
     *
     * Given: a persisted user client and an internal migration definition with the same client id.
     * Expect: migration fails without replacing the persisted definition.
     */
    @Test
    @DisplayName("rejects registered client type mismatch")
    void shouldRejectClientWhenRegisteredClientTypeDiffers() {
        // Arrange
        String clientId = "type-mismatch-" + UUID.randomUUID();
        this.clients.save(
            new RegisteredClientDefinition(
                RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .clientSecret(this.passwordEncoder.encode("secret"))
                    .clientName("User client")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .build(),
                RegisteredClientType.USER
            )
        );

        // Act + Assert
        assertThatThrownBy(() ->
            this.clientReconciler.reconcile(Map.of("type-mismatch", client(clientId, "secret")))
        ).hasMessageContaining("Registered client type mismatch");
    }

    /**
     * Verifies that explicit absence removes the managed registered-client row.
     *
     * Given: a managed client followed by an `absent` definition.
     * Expect: the registered-client row is deleted and migration does not manage OAuth state tables.
     */
    @Test
    @DisplayName("removes managed registered client when client is marked absent")
    void shouldRemoveManagedRegisteredClientWhenClientIsAbsent() {
        // Arrange
        String clientId = "absent-" + UUID.randomUUID();
        this.clientReconciler.reconcile(Map.of("absent", client(clientId, "secret")));

        // Act
        this.clientReconciler.reconcile(Map.of("absent", absentClient(clientId)));

        // Assert
        assertThat(this.clients.findByClientId(clientId)).isNull();
    }

    /**
     * Verifies that removing a client definition does not implicitly delete its managed registration.
     *
     * Given: a managed client followed by reconciliation with an empty client map.
     * Expect: the client remains persisted until an explicit `absent: true` definition is supplied.
     */
    @Test
    @DisplayName("retains managed clients omitted from configuration")
    void shouldRetainManagedClientWhenDefinitionIsRemoved() {
        // Arrange
        String clientId = "retained-" + UUID.randomUUID();
        this.clientReconciler.reconcile(Map.of("retained", client(clientId, "secret")));

        // Act
        this.clientReconciler.reconcile(Map.of());

        // Assert
        assertThat(this.storedClient(clientId)).isNotNull();
    }

    private RegisteredClient storedClient(String clientId) {
        return Objects.requireNonNull(this.clients.findByClientId(clientId));
    }

    private RegisteredClientDefinition storedDefinition(String clientId) {
        return Objects.requireNonNull(this.clients.findDefinitionByClientId(clientId));
    }

    private static MigrationProperties.ManagedClientProperties client(String clientId, String clientSecret) {
        var definition = new MigrationProperties.ManagedClientProperties();
        var registration = definition.getRegistration();
        registration.setClientId(clientId);
        registration.setClientSecret(clientSecret);
        registration.setClientName("Internal " + clientId);
        registration.setClientAuthenticationMethods(Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue()));
        registration.setAuthorizationGrantTypes(Set.of(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue()));
        registration.setRedirectUris(Set.of());
        registration.setPostLogoutRedirectUris(Set.of());
        registration.setScopes(Set.of());
        definition.setRequireProofKey(false);
        definition.setRequireAuthorizationConsent(false);
        definition.setType(RegisteredClientType.INTERNAL);
        return definition;
    }

    private static MigrationProperties.ManagedClientProperties absentClient(String clientId) {
        var definition = client(clientId, "unused");
        definition.setAbsent(true);
        return definition;
    }
}
