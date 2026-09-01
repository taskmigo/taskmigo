package io.taskmigo.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.taskmigo.PostgresTestConfiguration;
import io.taskmigo.user.SystemUser;
import io.taskmigo.user.UserService;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.springframework.web.client.RestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "taskmigo.security.signing-key-file=build/test-data/browser-auth-signing-key.pem",
        "taskmigo.security.signing-key-auto-create=true",
    }
)
@Import(PostgresTestConfiguration.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class InteractiveAuthenticationIntegrationTest {

    private final UserDetailsService userDetails;
    private final PasswordEncoder passwordEncoder;
    private final UserService users;

    @LocalServerPort
    private int port;

    InteractiveAuthenticationIntegrationTest(
        UserDetailsService userDetails,
        PasswordEncoder passwordEncoder,
        UserService users
    ) {
        this.userDetails = userDetails;
        this.passwordEncoder = passwordEncoder;
        this.users = users;
    }

    @Test
    @DisplayName("authenticates the persisted system user through the web security configuration")
    void shouldAuthenticateSystemUserWhenPersistedCredentialsAreValid() {
        var persisted = this.users.findForAuthentication(SystemUser.USERNAME).orElseThrow();
        var info = this.users.require(persisted.id());
        var principal = this.userDetails.loadUserByUsername(SystemUser.USERNAME);
        String persistedHash = Objects.requireNonNull(persisted.passwordHash());

        assertThat(info.username()).isEqualTo(SystemUser.USERNAME);
        assertThat(info.firstName()).isEqualTo(SystemUser.FIRST_NAME);
        assertThat(info.lastName()).isEqualTo(SystemUser.LAST_NAME);
        assertThat(info.emails()).isEmpty();
        assertThat(principal.getUsername()).isEqualTo(SystemUser.USERNAME);
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_SYSTEM");
        assertThat(this.passwordEncoder.matches("integration-password", persistedHash)).isTrue();
        assertThat(this.passwordEncoder.matches("integration-password", principal.getPassword())).isTrue();
    }

    @Test
    @DisplayName("exposes the OpenID Connect provider configuration")
    void shouldExposeOidcProviderConfigurationWhenDiscoveryEndpointIsRequested() {
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
