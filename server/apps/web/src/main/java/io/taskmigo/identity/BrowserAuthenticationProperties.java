package io.taskmigo.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "taskmigo.security.browser-authentication")
record BrowserAuthenticationProperties(
    boolean enabled,
    String clientSecret,
    @NotNull URI clientUrl,
    @Valid @NotNull DevelopmentUser developmentUser
) {
    URI redirectUri() {
        return this.clientUrl.resolve("/api/auth/callback");
    }

    URI postLogoutRedirectUri() {
        return this.clientUrl.resolve("/");
    }

    record DevelopmentUser(boolean enabled, @NotBlank String username, String password) {}
}
