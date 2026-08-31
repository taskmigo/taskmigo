package io.taskmigo.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.identity.oauth.InternalClientMetadata;
import io.taskmigo.organization.OrganizationService;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
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
    private final OrganizationService organizations;

    @LocalServerPort
    private int port;

    OAuthInfrastructureIntegrationTest(
        RegisteredClientRepository clients,
        PasswordEncoder passwordEncoder,
        OAuth2AuthorizationService authorizations,
        OrganizationService organizations
    ) {
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
        this.authorizations = authorizations;
        this.organizations = organizations;
    }

    @Test
    void managedClientCredentialsTokenCarriesServicePermissionsAndCanReadStatementCatalog() {
        String clientId = "managed-" + UUID.randomUUID();
        String clientSecret = "managed-secret";
        RegisteredClient client = this.managedClient(clientId, clientSecret);
        this.clients.save(client);
        UUID organizationId = this.organizations.create("statement-catalog-" + UUID.randomUUID(), "Statement Catalog");

        assertThat(InternalClientMetadata.isManaged(client)).isTrue();

        String token = this.accessToken(clientId, clientSecret);
        String response = Objects.requireNonNull(
            this.http()
                .get()
                .uri("/api/v0/organizations/{organizationId}/statements", organizationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .body(String.class)
        );

        assertThat(response)
            .contains("\"code\":\"resource.statements.retrieved\"")
            .contains("\"data\":[]");
        assertThat(this.authorizations.findByToken(token, OAuth2TokenType.ACCESS_TOKEN)).isNotNull();
    }

    @Test
    void managedClientCredentialsCanManageOrganizationAcl() {
        String clientId = "managed-acl-" + UUID.randomUUID();
        String clientSecret = "managed-acl-secret";
        this.clients.save(this.managedClient(clientId, clientSecret));
        String token = this.accessToken(clientId, clientSecret);
        UUID organizationId = this.organizations.create("acl-managed-" + UUID.randomUUID(), "Managed ACL Org");
        String policyName = "deny-project-list-" + UUID.randomUUID();

        var response = this.http()
            .put()
            .uri("/api/v0/organizations/{organizationId}/acl-policies/{policyName}", organizationId, policyName)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestAclPolicy())
            .retrieve()
            .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void scopeWithoutManagedClientMarkerCannotReadOrganizationStatementCatalog() {
        String clientId = "scope-only-" + UUID.randomUUID();
        String clientSecret = "scope-only-secret";
        this.clients.save(this.unmanagedClient(clientId, clientSecret));
        String token = this.accessToken(clientId, clientSecret);
        UUID organizationId = this.organizations.create("unmanaged-catalog-" + UUID.randomUUID(), "Unmanaged Catalog");

        assertThatThrownBy(() ->
            this.http()
                .get()
                .uri("/api/v0/organizations/{organizationId}/statements", organizationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .toBodilessEntity()
        ).isInstanceOf(HttpClientErrorException.Forbidden.class);
    }

    @Test
    void authorizationCodeRequestRedirectsAnonymousBrowserToLogin() {
        String clientId = "browser-" + UUID.randomUUID();
        String redirectUri = "http://127.0.0.1:3000/api/auth/callback";
        this.clients.save(this.browserClient(clientId, redirectUri));

        var response = this.httpWithoutRedirects()
            .get()
            .uri(uriBuilder ->
                uriBuilder
                    .path("/oauth2/authorize")
                    .queryParam("response_type", "code")
                    .queryParam("client_id", clientId)
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("scope", OidcScopes.OPENID)
                    .queryParam("state", "test-state")
                    .queryParam("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                    .queryParam("code_challenge_method", "S256")
                    .build()
            )
            .retrieve()
            .toBodilessEntity();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(Objects.requireNonNull(response.getHeaders().getFirst(HttpHeaders.LOCATION)))
            .endsWith("/login")
            .doesNotContain("error=invalid_request");
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

    private RegisteredClient browserClient(String clientId, String redirectUri) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId(clientId)
            .clientName("Browser")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(redirectUri)
            .scope(OidcScopes.OPENID)
            .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build())
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

    private RestClient httpWithoutRedirects() {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
        return RestClient.builder()
            .baseUrl("http://localhost:" + this.port)
            .requestFactory(new JdkClientHttpRequestFactory(client))
            .build();
    }

    private static Map<String, Object> requestAclPolicy() {
        return Map.of(
            "kind",
            "acl/request",
            "spec",
            Map.of(
                "target",
                Map.of("methods", List.of("GET"), "path", "/api/v0/projects"),
                "rules",
                Map.of("deny-authenticated", Map.of("effect", "deny", "when", Map.of("exists", "principal.id")))
            )
        );
    }

    private record TokenResponse(String access_token) {}
}
