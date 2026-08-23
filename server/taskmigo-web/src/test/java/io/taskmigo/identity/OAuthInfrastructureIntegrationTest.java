package io.taskmigo.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.identity.IdentityProperties.InternalClientDefinition;
import io.taskmigo.resource.PermissionCatalog;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "taskmigo.security.internal-clients.cli.client-id=integration-client",
        "taskmigo.security.internal-clients.cli.client-secret=integration-secret",
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
            jdbc.queryForObject(
                "select count(*) from oauth_client_management where registration_key = 'cli' and managed_by = 'SYSTEM'",
                Integer.class
            )
        ).isEqualTo(1);
        assertThat(
            jdbc.queryForObject(
                """
                select count(*)
                from oauth_service_principal_permissions permissions
                join oauth_client_management client
                  on client.registered_client_id = permissions.registered_client_id
                where client.registration_key = 'cli'
                  and permissions.permission_key = 'system.resources.manage'
                """,
                Integer.class
            )
        ).isEqualTo(1);
    }

    @Test
    void reconciliationIsIdempotent() {
        String registeredClientId = managedClientId("cli");
        String encodedSecret = encodedSecret(registeredClientId);

        reconciler.reconcile(Map.of("cli", definition("integration-client", "integration-secret", true, 1)));

        assertThat(managedClientId("cli")).isEqualTo(registeredClientId);
        assertThat(encodedSecret(registeredClientId)).isEqualTo(encodedSecret);
    }

    @Test
    void concurrentReconciliationCreatesOneRegistration() throws Exception {
        String registrationKey = "concurrent-" + UUID.randomUUID();
        var definitions = Map.of(registrationKey, definition(registrationKey, "concurrent-secret", true, 1));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> reconciler.reconcile(definitions));
            var second = executor.submit(() -> reconciler.reconcile(definitions));
            first.get();
            second.get();
        }

        assertThat(
            jdbc.queryForObject(
                "select count(*) from oauth_client_management where registration_key = ?",
                Integer.class,
                registrationKey
            )
        ).isEqualTo(1);
    }

    @Test
    void newerConfigurationCannotBeOverwrittenByAStaleInstance() {
        String registrationKey = "versioned-" + UUID.randomUUID();
        String clientId = registrationKey + "-client";
        reconciler.reconcile(Map.of(registrationKey, definition(clientId, "old-secret", true, 1)));
        reconciler.reconcile(Map.of(registrationKey, definition(clientId, "new-secret", true, 2)));

        reconciler.reconcile(Map.of(registrationKey, definition(clientId, "old-secret", true, 1)));

        String registeredClientId = managedClientId(registrationKey);
        assertThat(passwordEncoder.matches("new-secret", encodedSecret(registeredClientId))).isTrue();
        assertThat(
            jdbc.queryForObject(
                "select configuration_version from oauth_client_management where registration_key = ?",
                Long.class,
                registrationKey
            )
        ).isEqualTo(2);
    }

    @Test
    void sameVersionCannotDescribeDifferentConfiguration() {
        String registrationKey = "conflict-" + UUID.randomUUID();
        String clientId = registrationKey + "-client";
        reconciler.reconcile(Map.of(registrationKey, definition(clientId, "secret", true, 1)));

        assertThatThrownBy(() ->
            reconciler.reconcile(Map.of(registrationKey, definition(clientId, "secret", false, 1)))
        ).hasMessageContaining("without increasing configuration-version");
    }

    @Test
    void secretRotationRequiresANewerConfigurationVersion() {
        String registrationKey = "secret-conflict-" + UUID.randomUUID();
        String clientId = registrationKey + "-client";
        reconciler.reconcile(Map.of(registrationKey, definition(clientId, "first-secret", true, 1)));

        assertThatThrownBy(() ->
            reconciler.reconcile(Map.of(registrationKey, definition(clientId, "second-secret", true, 1)))
        ).hasMessageContaining("client-secret changed without increasing configuration-version");
    }

    @Test
    void disabledSystemClientIsUnavailableToTheTokenEndpoint() {
        String registrationKey = "disabled-" + UUID.randomUUID();
        String clientId = registrationKey + "-client";
        reconciler.reconcile(Map.of(registrationKey, definition(clientId, "secret", false, 1)));

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
            reconciler.reconcile(Map.of("system-" + UUID.randomUUID(), definition(clientId, "secret", true, 1)))
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

    private String managedClientId(String registrationKey) {
        return Objects.requireNonNull(
            jdbc.queryForObject(
                "select registered_client_id from oauth_client_management where registration_key = ?",
                String.class,
                registrationKey
            )
        );
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

    private static InternalClientDefinition definition(
        String clientId,
        String clientSecret,
        boolean enabled,
        long version
    ) {
        return new InternalClientDefinition(
            clientId,
            clientSecret,
            enabled,
            version,
            Set.of("taskmigo.api"),
            Set.of(ServicePrincipalPermissions.SYSTEM_RESOURCES_MANAGE)
        );
    }

    private record TokenResponse(String access_token) {}
}
