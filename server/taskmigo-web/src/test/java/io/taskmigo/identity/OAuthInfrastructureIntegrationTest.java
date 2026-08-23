package io.taskmigo.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.resource.PermissionCatalog;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
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
class OAuthInfrastructureIntegrationTest {

    @Autowired
    RegisteredClientRepository clients;

    @Autowired
    JdbcRegisteredClientRepository storedClients;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    InternalClientReconciler reconciler;

    @Autowired
    OAuth2AuthorizationService authorizations;

    @LocalServerPort
    int port;

    @Test
    void flywayCreatesTheSystemClient() {
        RegisteredClient client = storedClient("integration-client");
        assertThat(InternalClientMetadata.isManaged(client)).isTrue();
        assertThat(InternalClientMetadata.permissions(client)).containsExactly(
            ServicePrincipalPermissions.SYSTEM_RESOURCES_MANAGE
        );
    }

    @Test
    void reconciliationIsIdempotent() {
        String registeredClientId = managedClientId("integration-client");
        String encodedSecret = encodedSecret(registeredClientId);

        reconciler.reconcile(Map.of("cli", client("integration-client", "integration-secret")));

        assertThat(managedClientId("integration-client")).isEqualTo(registeredClientId);
        assertThat(encodedSecret(registeredClientId)).isEqualTo(encodedSecret);
    }

    @Test
    void concurrentReconciliationCreatesOneRegistration() throws Exception {
        String clientId = "concurrent-" + UUID.randomUUID();
        var configuredClients = Map.of("concurrent", client(clientId, "concurrent-secret"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> reconciler.reconcile(configuredClients));
            var second = executor.submit(() -> reconciler.reconcile(configuredClients));
            first.get();
            second.get();
        }

        assertThat(storedClient(clientId).getId()).isEqualTo("concurrent");
    }

    @Test
    void changedSecretIsUpsertedWithoutChangingTheRegistrationId() {
        String clientId = "upsert-" + UUID.randomUUID();
        reconciler.reconcile(Map.of("upsert", client(clientId, "old-secret")));
        String registeredClientId = managedClientId(clientId);
        reconciler.reconcile(Map.of("upsert", client(clientId, "new-secret")));

        assertThat(managedClientId(clientId)).isEqualTo(registeredClientId);
        assertThat(passwordEncoder.matches("new-secret", encodedSecret(registeredClientId))).isTrue();
    }

    @Test
    void duplicateClientIdsAreRejectedBeforeReconciliation() {
        String clientId = "duplicate-" + UUID.randomUUID();

        assertThatThrownBy(() ->
            reconciler.reconcile(
                Map.of("first", client(clientId, "first-secret"), "second", client(clientId, "second-secret"))
            )
        ).hasMessageContaining("Duplicate internal client-id");
    }

    @Test
    void systemReconciliationRefusesToAdoptUnmanagedClient() {
        String clientId = "unmanaged-" + UUID.randomUUID();
        clients.save(
            RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret(passwordEncoder.encode("secret"))
                .clientName("Unmanaged")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("taskmigo.api")
                .build()
        );

        assertThatThrownBy(() ->
            reconciler.reconcile(Map.of("unmanaged", client(clientId, "secret")))
        ).hasMessageContaining("Refusing to adopt unmanaged OAuth client");
    }

    @Test
    void clientCredentialsTokenCarriesServicePermissionsAndPersistsAuthorization() {
        String token = accessToken("integration-client", "integration-secret");

        String response = Objects.requireNonNull(
            http()
                .get()
                .uri("/api/v0/permissions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(String.class)
        );
        assertThat(response).contains(PermissionCatalog.PROJECT_READ);
        assertThat(authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN)).isNotNull();
    }

    @Test
    void scopeWithoutServicePermissionCannotAccessApi() {
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
        String token = accessToken(clientId, clientSecret);

        assertThatThrownBy(() ->
            http()
                .get()
                .uri("/api/v0/permissions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    private String accessToken(String clientId, String clientSecret) {
        TokenResponse response = Objects.requireNonNull(
            http()
                .post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&scope=taskmigo.api")
                .retrieve()
                .body(TokenResponse.class)
        );
        return response.access_token();
    }

    private RestClient http() {
        return RestClient.builder()
            .baseUrl("http://localhost:" + port)
            .build();
    }

    private String managedClientId(String clientId) {
        return storedClient(clientId).getId();
    }

    private RegisteredClient storedClient(String clientId) {
        return Objects.requireNonNull(storedClients.findByClientId(clientId));
    }

    private String encodedSecret(String registeredClientId) {
        return Objects.requireNonNull(
            Objects.requireNonNull(storedClients.findById(registeredClientId)).getClientSecret()
        );
    }

    private static Client client(String clientId, String clientSecret) {
        Client client = new Client();
        var registration = client.getRegistration();
        registration.setClientId(clientId);
        registration.setClientSecret(clientSecret);
        registration.setClientAuthenticationMethods(new LinkedHashSet<>(Set.of("client_secret_basic")));
        registration.setAuthorizationGrantTypes(new LinkedHashSet<>(Set.of("client_credentials")));
        registration.setScopes(new LinkedHashSet<>(Set.of("taskmigo.api")));
        return client;
    }

    private record TokenResponse(String access_token) {}
}
