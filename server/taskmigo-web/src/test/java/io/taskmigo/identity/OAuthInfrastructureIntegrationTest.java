package io.taskmigo.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.identity.InternalClientProperties.Definition;
import io.taskmigo.resource.PermissionCatalog;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "taskmigo.internal.clients[0].id=integration-client",
        "taskmigo.internal.clients[0].secret=integration-secret",
        "taskmigo.internal.clients[0].enabled=true",
        "taskmigo.internal.clients[0].generation=1",
        "taskmigo.security.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.security.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
class OAuthInfrastructureIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RegisteredClientRepository clients;

    @Autowired
    JdbcRegisteredClientRepository storedClients;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    InternalClientReconciler reconciler;

    @Autowired
    ExpiredAuthorizationCleaner authorizationCleaner;

    @LocalServerPort
    int port;

    @Test
    void flywayCreatesIdentitySchemaAndSystemClient() {
        assertThat(
            jdbc.queryForList(
                "select table_name from information_schema.tables " +
                    "where table_schema = 'public' and table_name like 'oauth%'",
                String.class
            )
        ).containsExactlyInAnyOrder("oauth2_registered_client", "oauth2_authorization", "oauth2_authorization_consent");

        RegisteredClient client = storedClient("integration-client");
        assertThat(InternalClientMetadata.isManaged(client)).isTrue();
        assertThat(InternalClientMetadata.isEnabled(client)).isTrue();
        assertThat(InternalClientMetadata.generation(client)).isEqualTo(1);
        assertThat(InternalClientMetadata.permissions(client)).containsExactly(
            ServicePrincipalPermissions.SYSTEM_RESOURCES_MANAGE
        );
    }

    @Test
    void reconciliationIsIdempotent() {
        String registeredClientId = managedClientId("integration-client");
        String encodedSecret = encodedSecret(registeredClientId);

        reconciler.reconcile(List.of(definition("integration-client", "integration-secret", true, 1)));

        assertThat(managedClientId("integration-client")).isEqualTo(registeredClientId);
        assertThat(encodedSecret(registeredClientId)).isEqualTo(encodedSecret);
    }

    @Test
    void concurrentReconciliationCreatesOneRegistration() throws Exception {
        String clientId = "concurrent-" + UUID.randomUUID();
        var definitions = List.of(definition(clientId, "concurrent-secret", true, 1));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> reconciler.reconcile(definitions));
            var second = executor.submit(() -> reconciler.reconcile(definitions));
            first.get();
            second.get();
        }

        assertThat(
            jdbc.queryForObject(
                "select count(*) from oauth2_registered_client where client_id = ?",
                Integer.class,
                clientId
            )
        ).isEqualTo(1);
    }

    @Test
    void newerConfigurationCannotBeOverwrittenByAStaleInstance() {
        String clientId = "versioned-" + UUID.randomUUID();
        reconciler.reconcile(List.of(definition(clientId, "old-secret", true, 1)));
        String registeredClientId = managedClientId(clientId);
        reconciler.reconcile(List.of(definition(clientId, "new-secret", true, 2)));

        reconciler.reconcile(List.of(definition(clientId, "old-secret", true, 1)));

        assertThat(managedClientId(clientId)).isEqualTo(registeredClientId);
        assertThat(passwordEncoder.matches("new-secret", encodedSecret(registeredClientId))).isTrue();
        assertThat(InternalClientMetadata.generation(storedClient(clientId))).isEqualTo(2);
    }

    @Test
    void duplicateClientIdsAreRejectedBeforeReconciliation() {
        String clientId = "duplicate-" + UUID.randomUUID();

        assertThatThrownBy(() ->
            reconciler.reconcile(
                List.of(definition(clientId, "first-secret", true, 1), definition(clientId, "second-secret", true, 2))
            )
        ).hasMessageContaining("Duplicate internal client-id");
    }

    @Test
    void sameGenerationCannotDescribeDifferentConfiguration() {
        String clientId = "conflict-" + UUID.randomUUID();
        reconciler.reconcile(List.of(definition(clientId, "secret", true, 1)));

        assertThatThrownBy(() ->
            reconciler.reconcile(List.of(definition(clientId, "secret", false, 1)))
        ).hasMessageContaining("without increasing generation");
    }

    @Test
    void secretRotationRequiresANewerGeneration() {
        String clientId = "secret-conflict-" + UUID.randomUUID();
        reconciler.reconcile(List.of(definition(clientId, "first-secret", true, 1)));

        assertThatThrownBy(() ->
            reconciler.reconcile(List.of(definition(clientId, "second-secret", true, 1)))
        ).hasMessageContaining("secret changed without increasing generation");
    }

    @Test
    void disabledSystemClientIsUnavailableToTheTokenEndpoint() {
        String clientId = "disabled-" + UUID.randomUUID();
        reconciler.reconcile(List.of(definition(clientId, "secret", false, 1)));

        assertThat(clients.findByClientId(clientId)).isNull();
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
            reconciler.reconcile(List.of(definition(clientId, "secret", true, 1)))
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
        assertThat(
            jdbc.queryForObject(
                "select count(*) from oauth2_authorization where principal_name = 'integration-client'",
                Integer.class
            )
        ).isGreaterThanOrEqualTo(1);
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

    @Test
    void cleanupDeletesExpiredClientCredentialsAuthorizations() {
        accessToken("integration-client", "integration-secret");
        jdbc.update(
            "update oauth2_authorization set access_token_expires_at = current_timestamp - interval '8 days' " +
                "where principal_name = 'integration-client'"
        );

        authorizationCleaner.clean();

        assertThat(
            jdbc.queryForObject(
                "select count(*) from oauth2_authorization where principal_name = 'integration-client'",
                Integer.class
            )
        ).isZero();
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
        return Objects.requireNonNull(
            jdbc.queryForObject("select id from oauth2_registered_client where client_id = ?", String.class, clientId)
        );
    }

    private RegisteredClient storedClient(String clientId) {
        return Objects.requireNonNull(storedClients.findByClientId(clientId));
    }

    private String encodedSecret(String registeredClientId) {
        return Objects.requireNonNull(
            jdbc.queryForObject(
                "select client_secret from oauth2_registered_client where id = ?",
                String.class,
                registeredClientId
            )
        );
    }

    private static Definition definition(String clientId, String clientSecret, boolean enabled, long generation) {
        return new Definition(clientId, clientSecret, enabled, generation);
    }

    private record TokenResponse(String access_token) {}
}
