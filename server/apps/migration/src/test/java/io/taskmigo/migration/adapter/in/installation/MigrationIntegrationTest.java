package io.taskmigo.migration.adapter.in.installation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.authorization.provisioning.application.port.in.api.AuthorizationProvisioningService;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.application.port.in.api.RoleService;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectRoleQueryService;
import io.taskmigo.identity.group.application.port.in.api.GroupService;
import io.taskmigo.identity.provisioning.application.port.in.api.GroupProvisioningService;
import io.taskmigo.identity.provisioning.application.port.in.api.IdentityProvisioningService;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.test.context.TestConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    properties = {
        "TM_SYSTEM_PASSWORD=integration-password",
        "TM_BROWSER_CLIENT_SECRET=browser-integration-secret",
        "TM_BROWSER_HOST_NAME=http://localhost:3000",
        "TM_BROWSER_AUTHENTICATION_ENABLED=true",
    }
)
@Import(PostgresTestConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MigrationIntegrationTest {

    private final Flyway flyway;
    private final JdbcRegisteredClientRepository clients;
    private final MigrationRunner migration;
    private final MigrationResourceLoader resources;
    private final PasswordEncoder passwordEncoder;
    private final UserService users;
    private final RoleService roles;
    private final StatementService statements;
    private final AuthorizationProvisioningService authorizationProvisioning;
    private final IdentityProvisioningService identityProvisioning;
    private final GroupProvisioningService groups;
    private final GroupService runtimeGroups;
    private final SubjectRoleQueryService effectiveRoles;
    private final JdbcTemplate jdbc;

    MigrationIntegrationTest(
        Flyway flyway,
        JdbcRegisteredClientRepository clients,
        MigrationRunner migration,
        MigrationResourceLoader resources,
        PasswordEncoder passwordEncoder,
        UserService users,
        RoleService roles,
        StatementService statements,
        AuthorizationProvisioningService authorizationProvisioning,
        IdentityProvisioningService identityProvisioning,
        GroupProvisioningService groups,
        GroupService runtimeGroups,
        SubjectRoleQueryService effectiveRoles,
        JdbcTemplate jdbc
    ) {
        this.flyway = flyway;
        this.clients = clients;
        this.migration = migration;
        this.resources = resources;
        this.passwordEncoder = passwordEncoder;
        this.users = users;
        this.roles = roles;
        this.statements = statements;
        this.authorizationProvisioning = authorizationProvisioning;
        this.identityProvisioning = identityProvisioning;
        this.groups = groups;
        this.runtimeGroups = runtimeGroups;
        this.effectiveRoles = effectiveRoles;
        this.jdbc = jdbc;
    }

    /**
     * Verifies: migration applies the canonical schema and managed clients.
     * Given: a fresh Testcontainers database and flat YAML resources.
     * Expect: version 1, the encoded system password, and the browser client exists.
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
        assertThat(this.effectiveRoles.effectiveRolesForPrincipal(system.id()))
            .extracting(RoleInfo::code)
            .contains("basic-user");

        RegisteredClient browser = this.storedClient("browser");
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
        assertThat(
            this.passwordEncoder.matches(
                "browser-integration-secret",
                Objects.requireNonNull(browser.getClientSecret())
            )
        ).isTrue();
    }

    /**
     * Verifies: the flat resource loader binds OAuth clients to Spring's native property class.
     * Given: security.yaml with a registration map and placeholder values.
     * Expect: the resolved native Client contains no implicit API scope.
     */
    @Test
    @DisplayName("binds flat security YAML to native Spring OAuth client properties")
    void shouldBindNativeClientPropertiesWhenSecurityYamlIsLoaded() {
        Client browser = Objects.requireNonNull(this.resources.load().clients().get("browser"));

        assertThat(browser.getRegistration().getClientId()).isEqualTo("browser");
        assertThat(browser.getRegistration().getClientSecret()).isEqualTo("browser-integration-secret");
        assertThat(browser.getRegistration().getScopes()).containsExactlyInAnyOrder(
            OidcScopes.OPENID,
            OidcScopes.PROFILE
        );
    }

    /**
     * Verifies: re-running the complete migration is idempotent for initialized credentials.
     * Given: already-provisioned managed resources with persisted User and OAuth credential hashes.
     * Expect: database identifiers and both persisted credential hashes remain unchanged.
     */
    @Test
    @DisplayName("preserves managed client and user state during reconciliation")
    void shouldPreserveStateWhenReconciliationRunsAgain() {
        String browserId = this.storedClient("browser").getId();
        String passwordHash = Objects.requireNonNull(
            this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().passwordHash()
        );
        String clientSecretHash = Objects.requireNonNull(this.storedClient("browser").getClientSecret());

        this.migration.migrate();

        assertThat(this.storedClient("browser").getId()).isEqualTo(browserId);
        assertThat(this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().passwordHash()).isEqualTo(
            passwordHash
        );
        assertThat(this.storedClient("browser").getClientSecret()).isEqualTo(clientSecretHash);
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
     * Verifies: Identity provisioning treats its password input as an initial encoded credential.
     * Given: a User created with an initial hash and then reconciled with profile changes only.
     * Expect: the same User id and initial password hash are preserved while profile state is updated.
     */
    @Test
    @DisplayName("upserts users from migration data")
    void shouldUpsertManagedUserWhenUserIsMissingOrPresent() {
        String username = "migration-user";
        UUID roleId = this.authorizationProvisioning.requireRole("system-operator");
        UUID createdId = this.identityProvisioning
            .reconcileUser(
                username,
                this.passwordEncoder.encode("migration-password"),
                Set.of("MIGRATION@EXAMPLE.COM"),
                "Migration",
                "User",
                Set.of(roleId),
                Set.of()
            )
            .id();
        String passwordHash = Objects.requireNonNull(
            this.users.findForAuthentication(username).orElseThrow().passwordHash()
        );

        UUID reconciledId = this.identityProvisioning
            .reconcileUser(username, null, Set.of("updated@example.com"), "Updated", "User", Set.of(roleId), Set.of())
            .id();

        assertThat(reconciledId).isEqualTo(createdId);
        assertThat(this.users.require(createdId))
            .extracting(UserInfo::firstName, UserInfo::emails)
            .containsExactly("Updated", Set.of("updated@example.com"));
        assertThat(this.users.findForAuthentication(username).orElseThrow().passwordHash()).isEqualTo(passwordHash);
    }

    /**
     * Verifies that declarative absence removes dependency-linked managed resources safely.
     *
     * Given: one managed Statement referenced by a Role, Group, and User, followed by the same definitions marked absent.
     * Expect: reconciliation removes User, Group, Role, and Statement without violating dependency constraints.
     */
    @Test
    @DisplayName("removes dependency-linked resources when managed definitions are absent")
    void shouldRemoveManagedResourcesWhenDefinitionsAreAbsent() {
        // Arrange
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String statementCode = "phase1_stmt_" + suffix;
        String roleCode = "phase1-role-" + suffix;
        String groupCode = "phase1-group-" + suffix;
        String username = "phase1-user-" + suffix;
        var statement = new MigrationResourceLoader.Statement(
            statementCode,
            null,
            "allow",
            "request",
            new MigrationResourceLoader.Target(new MigrationResourceLoader.Api("GET", "/api/v0/users")),
            "return true;",
            false
        );
        var role = new MigrationResourceLoader.Role(roleCode, "Phase 1 Role", null, List.of(statementCode), false);
        var group = new MigrationResourceLoader.Group(groupCode, "Phase 1 Group", null, List.of(roleCode), false);
        var user = new MigrationResourceLoader.User(
            username,
            null,
            List.of(),
            "Phase",
            "One",
            List.of(roleCode),
            List.of(groupCode),
            false
        );
        this.migration.reconcile(
            new MigrationResourceLoader.MigrationResources(
                List.of(user),
                List.of(role),
                List.of(statement),
                List.of(group),
                Map.of()
            )
        );
        var absentStatement = new MigrationResourceLoader.Statement(
            statementCode,
            null,
            "allow",
            "request",
            new MigrationResourceLoader.Target(new MigrationResourceLoader.Api("GET", "/api/v0/users")),
            "return true;",
            true
        );
        var absentRole = new MigrationResourceLoader.Role(roleCode, "Phase 1 Role", null, List.of(), true);
        var absentGroup = new MigrationResourceLoader.Group(groupCode, "Phase 1 Group", null, List.of(), true);
        var absentUser = new MigrationResourceLoader.User(
            username,
            null,
            List.of(),
            "Phase",
            "One",
            List.of(),
            List.of(),
            true
        );

        // Act
        this.migration.reconcile(
            new MigrationResourceLoader.MigrationResources(
                List.of(absentUser),
                List.of(absentRole),
                List.of(absentStatement),
                List.of(absentGroup),
                Map.of()
            )
        );

        // Assert
        assertThat(this.users.findForAuthentication(username)).isEmpty();
        assertThat(this.groups.deleteGroup(groupCode)).isFalse();
        assertThatThrownBy(() -> this.authorizationProvisioning.requireRole(roleCode)).hasMessageContaining(
            "Managed authorization Role does not exist"
        );
        assertThat(this.statements.list(1, 100).items()).extracting(StatementInfo::code).doesNotContain(statementCode);
    }

    /**
     * Verifies that managed Group deletion rebuilds transitive closure rather than relying only on FK cascades.
     *
     * Given: a runtime hierarchy root -> middle -> leaf and managed deletion of the middle Group by stable code.
     * Expect: root-to-leaf closure is removed while reflexive closure for the remaining Groups is preserved.
     */
    @Test
    @DisplayName("rebuilds Group closure when managed deletion removes an intermediate Group")
    void shouldRebuildGroupClosureWhenManagedDeletionRemovesIntermediateGroup() {
        // Arrange
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String rootCode = "root-" + suffix;
        String middleCode = "middle-" + suffix;
        String leafCode = "leaf-" + suffix;
        UUID root = this.runtimeGroups.create(rootCode, "Root", null);
        UUID middle = this.runtimeGroups.create(middleCode, "Middle", null);
        UUID leaf = this.runtimeGroups.create(leafCode, "Leaf", null);
        this.runtimeGroups.setChildGroups(root, Set.of(middle));
        this.runtimeGroups.setChildGroups(middle, Set.of(leaf));
        assertThat(this.closureCount(root, leaf)).isEqualTo(1);

        // Act
        boolean removed = this.groups.deleteGroup(middleCode);

        // Assert
        assertThat(removed).isTrue();
        assertThat(this.closureCount(root, leaf)).isZero();
        assertThat(this.closureCount(root, root)).isEqualTo(1);
        assertThat(this.closureCount(leaf, leaf)).isEqualTo(1);
    }

    /**
     * Verifies that managed Role deletion rebuilds transitive closure instead of relying only on FK cascades.
     *
     * Given: a runtime Role hierarchy root -> middle -> leaf.
     * Expect: deleting the managed middle Role removes stale root-to-leaf closure while preserving reflexive closure.
     */
    @Test
    @DisplayName("rebuilds Role closure when managed deletion removes an intermediate Role")
    void shouldRebuildRoleClosureWhenManagedDeletionRemovesIntermediateRole() {
        // Arrange
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String rootCode = "role-root-" + suffix;
        String middleCode = "role-middle-" + suffix;
        String leafCode = "role-leaf-" + suffix;
        UUID root = this.roles.createRole(rootCode, "Root", null, Set.of());
        UUID middle = this.roles.createRole(middleCode, "Middle", null, Set.of());
        UUID leaf = this.roles.createRole(leafCode, "Leaf", null, Set.of());
        this.roles.setChildRoles(root, Set.of(middle));
        this.roles.setChildRoles(middle, Set.of(leaf));
        assertThat(this.roleClosureCount(root, leaf)).isEqualTo(1);

        // Act
        boolean removed = this.authorizationProvisioning.deleteRole(middleCode);

        // Assert
        assertThat(removed).isTrue();
        assertThat(this.roleClosureCount(root, leaf)).isZero();
        assertThat(this.roleClosureCount(root, root)).isEqualTo(1);
        assertThat(this.roleClosureCount(leaf, leaf)).isEqualTo(1);
    }

    /**
     * Verifies that managed OAuth client credentials rotate when desired secret input changes.
     *
     * Given: an existing managed client reconciled first with one raw secret and then with a different raw secret.
     * Expect: the persisted hash changes, matches the new secret, and no longer matches the previous secret.
     */
    @Test
    @DisplayName("rotates a managed OAuth client secret when configured secret changes")
    void shouldRotateManagedClientSecretWhenConfiguredSecretChanges() {
        // Arrange
        String clientId = "rotation-" + UUID.randomUUID();
        this.migration.reconcile(resources(Map.of("rotation", client(clientId, "initial-secret"))));
        String initialHash = Objects.requireNonNull(this.storedClient(clientId).getClientSecret());

        // Act
        this.migration.reconcile(resources(Map.of("rotation", client(clientId, "rotated-secret"))));
        String rotatedHash = Objects.requireNonNull(this.storedClient(clientId).getClientSecret());

        // Assert
        assertThat(rotatedHash).isNotEqualTo(initialHash);
        assertThat(this.passwordEncoder.matches("rotated-secret", rotatedHash)).isTrue();
        assertThat(this.passwordEncoder.matches("initial-secret", rotatedHash)).isFalse();
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
        var configuredClients = Map.of("concurrent", client(clientId, "concurrent-secret"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> this.migration.reconcile(resources(configuredClients)));
            var second = executor.submit(() -> this.migration.reconcile(resources(configuredClients)));
            first.get();
            second.get();
        }

        assertThat(this.storedClient(clientId).getId()).isEqualTo("concurrent");
    }

    /**
     * Verifies: a managed OAuth client emits events only when its configured data changes.
     * Given: a new internal client, followed by a changed configuration and then the same configuration again.
     * Expect: the events are `added` and `updated` only, and no secret is present in either event.
     */
    @Test
    @DisplayName("writes ECS JSON events only for added and changed clients")
    void shouldWriteJsonChangeEventsWhenInternalClientDataChanges(CapturedOutput output) throws Exception {
        // Arrange
        String clientId = "logging-" + UUID.randomUUID();
        Client initialClient = client(clientId, "logging-secret");
        Client changedClient = client(clientId, "logging-secret");
        changedClient.getRegistration().setClientName("Changed logging client");

        // Act
        this.migration.reconcile(resources(Map.of("logging", initialClient)));
        this.migration.reconcile(resources(Map.of("logging", changedClient)));
        this.migration.reconcile(resources(Map.of("logging", changedClient)));

        // Assert
        var events = output
            .getAll()
            .lines()
            .filter(line -> line.contains("\"key\":\"" + clientId + "\""))
            .map(MigrationIntegrationTest::parseJson)
            .toList();
        assertThat(events).hasSize(2);
        assertThat(events)
            .extracting(node -> node.path("event").path("type").asString())
            .containsOnly("change");
        assertThat(events)
            .extracting(node -> node.path("event").path("action").asString())
            .containsExactly("added", "updated");
        assertThat(events)
            .extracting(node -> node.path("taskmigo").path("migration").path("resource").path("type").asString())
            .containsOnly("oauth-client");
        assertThat(events)
            .extracting(node -> node.path("taskmigo").path("migration").path("resource").path("key").asString())
            .containsOnly(clientId);
        assertThat(output.getAll()).doesNotContain("logging-secret");
    }

    /**
     * Verifies: a failed transaction does not publish changes that were rolled back.
     * Given: a transaction that saves one client before refusing to adopt an unmanaged client.
     * Expect: the first client is absent from persistence and no change event is emitted for it.
     */
    @Test
    @DisplayName("does not log client changes when reconciliation rolls back")
    void shouldNotWriteChangeEventWhenInternalClientReconciliationRollsBack(CapturedOutput output) {
        // Arrange
        String managedClientId = "rollback-" + UUID.randomUUID();
        String unmanagedClientId = "rollback-unmanaged-" + UUID.randomUUID();
        this.clients.save(
            RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(unmanagedClientId)
                .clientSecret(this.passwordEncoder.encode("rollback-secret"))
                .clientName("Unmanaged")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build()
        );
        var configuredClients = Map.of(
            "a-managed",
            client(managedClientId, "managed-secret"),
            "b-unmanaged",
            client(unmanagedClientId, "unmanaged-secret")
        );

        // Act
        assertThatThrownBy(() -> this.migration.reconcile(resources(configuredClients))).hasMessageContaining(
            "Refusing to adopt unmanaged OAuth client"
        );

        // Assert
        assertThat(this.clients.findByClientId(managedClientId)).isNull();
        assertThat(output.getAll()).doesNotContain(managedClientId);
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
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/api/auth/callback")
                .postLogoutRedirectUri("http://localhost:3000/")
                .scope(OidcScopes.OPENID)
                .scope(OidcScopes.PROFILE)
                .build()
        );

        assertThatThrownBy(() ->
            this.migration.reconcile(resources(Map.of("unmanaged", client(clientId, "secret"))))
        ).hasMessageContaining("Refusing to adopt unmanaged OAuth client");
    }

    private int closureCount(UUID ancestorId, UUID descendantId) {
        return Objects.requireNonNull(
            this.jdbc.queryForObject(
                "select count(*) from group_hierarchy_closure where ancestor_group_id = ? and descendant_group_id = ?",
                Integer.class,
                ancestorId,
                descendantId
            )
        );
    }

    private int roleClosureCount(UUID ancestorId, UUID descendantId) {
        return Objects.requireNonNull(
            this.jdbc.queryForObject(
                "select count(*) from role_hierarchy_closure where ancestor_role_id = ? and descendant_role_id = ?",
                Integer.class,
                ancestorId,
                descendantId
            )
        );
    }

    private static MigrationResourceLoader.MigrationResources resources(Map<String, Client> clients) {
        return new MigrationResourceLoader.MigrationResources(List.of(), List.of(), List.of(), List.of(), clients);
    }

    private RegisteredClient storedClient(String clientId) {
        return Objects.requireNonNull(this.clients.findByClientId(clientId));
    }

    private static JsonNode parseJson(String line) {
        return JsonMapper.builder().build().readTree(line);
    }

    private static Client client(String clientId, String clientSecret) {
        Client client = new Client();
        var registration = client.getRegistration();
        registration.setClientId(clientId);
        registration.setClientSecret(clientSecret);
        registration.setClientAuthenticationMethods(new LinkedHashSet<>(Set.of("client_secret_basic")));
        registration.setAuthorizationGrantTypes(new LinkedHashSet<>(Set.of("authorization_code", "refresh_token")));
        registration.setRedirectUris(new LinkedHashSet<>(Set.of("http://localhost:3000/api/auth/callback")));
        registration.setPostLogoutRedirectUris(new LinkedHashSet<>(Set.of("http://localhost:3000/")));
        registration.setScopes(new LinkedHashSet<>(Set.of("openid", "profile")));
        client.setRequireProofKey(true);
        client.setRequireAuthorizationConsent(false);
        return client;
    }
}
