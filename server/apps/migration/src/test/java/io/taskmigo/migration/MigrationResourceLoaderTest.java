package io.taskmigo.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
import org.springframework.mock.env.MockEnvironment;

class MigrationResourceLoaderTest {

    /**
     * Verifies that migration resources use the declared defaults when their environment variables are absent.
     * Given: an environment without migration credential or client URL properties.
     * Expect: the built-in password, client secret, and localhost redirect URLs are resolved without a leading dash.
     */
    @Test
    @DisplayName("uses resource defaults when migration environment variables are absent")
    void shouldUseResourceDefaultsWhenEnvironmentVariablesAreAbsent() {
        // Arrange
        var loader = new MigrationResourceLoader(new MockEnvironment());

        // Act
        var resources = loader.load();
        Client browser = Objects.requireNonNull(resources.clients().get("browser"));

        // Assert
        assertThat(resources.users())
            .filteredOn(user -> user.username().equals("system"))
            .extracting(MigrationResourceLoader.User::password)
            .containsExactly("{noop}local-password");
        assertThat(browser.getRegistration().getClientSecret()).isEqualTo("{noop}local-secret");
        assertThat(browser.getRegistration().getRedirectUris()).containsExactly("http://localhost/api/auth/callback");
        assertThat(browser.getRegistration().getPostLogoutRedirectUris()).containsExactly("http://localhost/");
    }
}
