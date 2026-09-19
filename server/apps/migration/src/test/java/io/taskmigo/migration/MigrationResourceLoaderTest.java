package io.taskmigo.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
import org.springframework.mock.env.MockEnvironment;

class MigrationResourceLoaderTest {

    /**
     * Verifies that migration resources preserve raw credentials for application-side hashing.
     *
     * Given: explicit raw system-user and browser-client credentials plus a browser host.
     * Expect: YAML placeholders resolve to the raw values without adding an encoder prefix.
     */
    @Test
    @DisplayName("loads raw migration credentials from the environment")
    void shouldLoadRawCredentialsWhenEnvironmentPropertiesArePresent() {
        // Arrange
        var environment = new MockEnvironment()
            .withProperty("TM_SYSTEM_PASSWORD", "raw-system-password")
            .withProperty("TM_BROWSER_CLIENT_SECRET", "raw-browser-secret")
            .withProperty("TM_BROWSER_HOST_NAME", "http://localhost");
        var loader = new MigrationResourceLoader(environment);

        // Act
        var resources = loader.load();
        Client browser = Objects.requireNonNull(resources.clients().get("browser"));

        // Assert
        assertThat(resources.users())
            .filteredOn(user -> user.username().equals("system"))
            .extracting(MigrationResourceLoader.User::password)
            .containsExactly("raw-system-password");
        assertThat(browser.getRegistration().getClientSecret()).isEqualTo("raw-browser-secret");
        assertThat(browser.getRegistration().getRedirectUris()).containsExactly("http://localhost/api/auth/callback");
        assertThat(browser.getRegistration().getPostLogoutRedirectUris()).containsExactly("http://localhost/");
    }

    /**
     * Verifies that disabling browser authentication removes browser-client provisioning requirements.
     *
     * Given: browser authentication is disabled and no browser client secret or host is configured.
     * Expect: the resource loader returns no managed OAuth clients while still loading identity resources.
     */
    @Test
    @DisplayName("skips browser client resources when browser authentication is disabled")
    void shouldSkipBrowserClientWhenBrowserAuthenticationIsDisabled() {
        // Arrange
        var environment = new MockEnvironment()
            .withProperty("TM_SYSTEM_PASSWORD", "raw-system-password")
            .withProperty("TM_BROWSER_AUTHENTICATION_ENABLED", "false");
        var loader = new MigrationResourceLoader(environment);

        // Act
        var resources = loader.load();

        // Assert
        assertThat(resources.clients()).isEmpty();
        assertThat(resources.users()).extracting(MigrationResourceLoader.User::username).contains("system");
    }
}
