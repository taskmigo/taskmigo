package io.taskmigo.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.PostgresTestConfiguration;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "taskmigo.security.browser-authentication.enabled=true",
        "taskmigo.security.browser-authentication.client-secret=browser-integration-secret",
        "taskmigo.security.browser-authentication.client-url=http://localhost:3000",
        "taskmigo.security.browser-authentication.development-user.enabled=true",
        "taskmigo.security.browser-authentication.development-user.username=developer",
        "taskmigo.security.browser-authentication.development-user.password=integration-password",
        "taskmigo.security.signing-key-file=build/test-data/browser-auth-signing-key.pem",
        "taskmigo.security.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class BrowserAuthenticationIntegrationTest {

    private final RegisteredClientRepository clients;
    private final UserDetailsService users;
    private final PasswordEncoder passwordEncoder;

    @LocalServerPort
    private int port;

    BrowserAuthenticationIntegrationTest(
        RegisteredClientRepository clients,
        UserDetailsService users,
        PasswordEncoder passwordEncoder
    ) {
        this.clients = clients;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Test
    void browserClientUsesAuthorizationCodePkceAndRotatingRefreshTokens() {
        RegisteredClient client = Objects.requireNonNull(this.clients.findByClientId(BrowserClientMetadata.CLIENT_ID));

        assertThat(BrowserClientMetadata.isManaged(client)).isTrue();
        assertThat(client.getClientAuthenticationMethods()).containsExactly(
            ClientAuthenticationMethod.CLIENT_SECRET_BASIC
        );
        assertThat(client.getAuthorizationGrantTypes()).containsExactlyInAnyOrder(
            AuthorizationGrantType.AUTHORIZATION_CODE,
            AuthorizationGrantType.REFRESH_TOKEN
        );
        assertThat(client.getRedirectUris()).containsExactly("http://localhost:3000/api/auth/callback");
        assertThat(client.getPostLogoutRedirectUris()).containsExactly("http://localhost:3000/");
        assertThat(client.getScopes()).containsExactlyInAnyOrderElementsOf(
            Set.of(OidcScopes.OPENID, OidcScopes.PROFILE, BrowserClientMetadata.API_SCOPE)
        );
        assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isFalse();
        assertThat(client.getTokenSettings().isReuseRefreshTokens()).isFalse();
    }

    @Test
    void developmentUserIsOnlyProvisionedThroughExplicitConfiguration() {
        var user = this.users.loadUserByUsername("developer");

        assertThat(this.passwordEncoder.matches("integration-password", user.getPassword())).isTrue();
    }

    @Test
    void oidcProviderConfigurationIsExposed() {
        String response = Objects.requireNonNull(
            RestClient.builder()
                .baseUrl("http://localhost:" + this.port)
                .build()
                .get()
                .uri("/.well-known/openid-configuration")
                .retrieve()
                .body(String.class)
        );

        assertThat(response)
            .contains("authorization_endpoint")
            .contains("token_endpoint")
            .contains("jwks_uri")
            .contains("end_session_endpoint");
    }
}
