package io.taskmigo.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.identity.oauth.InternalClientMetadata;
import io.taskmigo.user.SystemUser;
import io.taskmigo.user.UserService;
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
    private final PasswordEncoder passwordEncoder;
    private final UserService users;

    BootstrapIntegrationTest(
        Flyway flyway,
        JdbcRegisteredClientRepository clients,
        InternalClientReconciler internalClients,
        BrowserClientReconciler browserClient,
        SystemUserReconciler systemUser,
        PasswordEncoder passwordEncoder,
        UserService users
    ) {
        this.flyway = flyway;
        this.clients = clients;
        this.internalClients = internalClients;
        this.browserClient = browserClient;
        this.systemUser = systemUser;
        this.passwordEncoder = passwordEncoder;
        this.users = users;
    }

    @Test
    @DisplayName("installs the schema, system user, and managed OAuth clients")
    void shouldInstallRequiredStateWhenBootstrapRuns() {
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
    @DisplayName("preserves managed client and user state during reconciliation")
    void shouldPreserveStateWhenReconciliationRunsAgain() {
        String internalId = this.storedClient("integration-client").getId();
        String browserId = this.storedClient(BrowserClientMetadata.CLIENT_ID).getId();
        String passwordHash = Objects.requireNonNull(
            this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().passwordHash()
        );

        this.internalClients.reconcile(Map.of("cli", client("integration-client", "integration-secret")));
        this.browserClient.reconcile();
        this.systemUser.reconcile();

        assertThat(this.storedClient("integration-client").getId()).isEqualTo(internalId);
        assertThat(this.storedClient(BrowserClientMetadata.CLIENT_ID).getId()).isEqualTo(browserId);
        assertThat(this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow().passwordHash()).isEqualTo(
            passwordHash
        );
    }

    @Test
    @DisplayName("creates one registration during concurrent internal client reconciliation")
    void shouldCreateOneRegistrationWhenInternalClientReconciliationIsConcurrent() throws Exception {
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
