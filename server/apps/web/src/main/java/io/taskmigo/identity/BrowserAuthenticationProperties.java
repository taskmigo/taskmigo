package io.taskmigo.identity;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "taskmigo.security.browser-authentication")
record BrowserAuthenticationProperties(boolean enabled, String clientSecret, @NotNull URI clientUrl) {
    URI redirectUri() {
        return this.clientUrl.resolve("/api/auth/callback");
    }

    URI postLogoutRedirectUri() {
        return this.clientUrl.resolve("/");
    }
}
