package io.taskmigo.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.access.PermissionCatalog;
import io.taskmigo.identity.oauth.InternalClientMetadata;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "taskmigo.security.signing-key-file=build/test-data/oauth-signing-key.pem",
        "taskmigo.security.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class OAuthInfrastructureIntegrationTest {

    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2AuthorizationService authorizations;

    @LocalServerPort
    private int port;

    OAuthInfrastructureIntegrationTest(
        RegisteredClientRepository clients,
        PasswordEncoder passwordEncoder,
        OAuth2AuthorizationService authorizations
    ) {
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
        this.authorizations = authorizations;
    }

    @Test
    void managedClientCredentialsTokenCarriesServicePermissionsAndPersistsAuthorization() {
        String clientId = "managed-" + UUID.randomUUID();
        String clientSecret = "managed-secret";
        RegisteredClient client = this.managedClient(clientId, clientSecret);
        this.clients.save(client);

        assertThat(InternalClientMetadata.isManaged(client)).isTrue();

        String token = this.accessToken(clientId, clientSecret);
        String response = Objects.requireNonNull(
            this.http()
                .get()
                .uri("/api/v0/permissions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(String.class)
        );

        assertThat(response).contains(PermissionCatalog.PROJECT_READ);
        assertThat(this.authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN)).isNotNull();
    }

    @Test
    void scopeWithoutManagedClientMarkerCannotAccessApi() {
        String clientId = "scope-only-" + UUID.randomUUID();
        String clientSecret = "scope-only-secret";
        this.clients.save(this.unmanagedClient(clientId, clientSecret));
        String token = this.accessToken(clientId, clientSecret);

        assertThatThrownBy(() ->
            this.http()
                .get()
                .uri("/api/v0/permissions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    private RegisteredClient managedClient(String clientId, String clientSecret) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(clientId)
            .clientSecret(this.passwordEncoder.encode(clientSecret))
            .clientName("Managed")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope(InternalClientMetadata.API_SCOPE)
            .clientSettings(InternalClientMetadata.settings(false, false))
            .build();
    }

    private RegisteredClient unmanagedClient(String clientId, String clientSecret) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(clientId)
            .clientSecret(this.passwordEncoder.encode(clientSecret))
            .clientName("Unmanaged")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .scope(InternalClientMetadata.API_SCOPE)
            .build();
    }

    private String accessToken(String clientId, String clientSecret) {
        TokenResponse response = Objects.requireNonNull(
            this.http()
                .post()
                .uri("/oauth2/token")
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&scope=" + InternalClientMetadata.API_SCOPE)
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
