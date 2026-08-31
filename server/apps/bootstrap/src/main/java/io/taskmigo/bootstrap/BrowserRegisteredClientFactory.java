package io.taskmigo.bootstrap;

import java.time.Duration;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

@Component
final class BrowserRegisteredClientFactory {

    private static final Duration ACCESS_TOKEN_TIME_TO_LIVE = Duration.ofMinutes(5);
    private static final Duration REFRESH_TOKEN_TIME_TO_LIVE = Duration.ofHours(8);

    private final PasswordEncoder passwordEncoder;

    BrowserRegisteredClientFactory(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    RegisteredClient create(BrowserAuthenticationProperties properties, @Nullable RegisteredClient existing) {
        String secret = requiredSecret(properties);
        RegisteredClient.Builder builder =
            existing == null
                ? RegisteredClient.withId("taskmigo-browser-client").clientId(BrowserClientMetadata.CLIENT_ID)
                : RegisteredClient.from(existing).clientId(BrowserClientMetadata.CLIENT_ID);

        return builder
            .clientSecret(this.encodedSecret(secret, existing))
            .clientName("Taskmigo browser client")
            .clientAuthenticationMethods(methods -> {
                methods.clear();
                methods.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            })
            .authorizationGrantTypes(grants -> {
                grants.clear();
                grants.add(AuthorizationGrantType.AUTHORIZATION_CODE);
                grants.add(AuthorizationGrantType.REFRESH_TOKEN);
            })
            .redirectUris(uris -> {
                uris.clear();
                uris.add(properties.redirectUri().toString());
            })
            .postLogoutRedirectUris(uris -> {
                uris.clear();
                uris.add(properties.postLogoutRedirectUri().toString());
            })
            .scopes(scopes -> {
                scopes.clear();
                scopes.addAll(Set.of(OidcScopes.OPENID, OidcScopes.PROFILE, BrowserClientMetadata.API_SCOPE));
            })
            .clientSettings(BrowserClientMetadata.settings())
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenTimeToLive(ACCESS_TOKEN_TIME_TO_LIVE)
                    .refreshTokenTimeToLive(REFRESH_TOKEN_TIME_TO_LIVE)
                    .reuseRefreshTokens(false)
                    .build()
            )
            .build();
    }

    private static String requiredSecret(BrowserAuthenticationProperties properties) {
        if (properties.clientSecret().isBlank()) {
            throw new IllegalStateException(
                "Browser OAuth client secret must not be blank when browser authentication is enabled"
            );
        }
        return properties.clientSecret();
    }

    private String encodedSecret(String secret, @Nullable RegisteredClient existing) {
        String existingSecret = existing == null ? null : existing.getClientSecret();
        if (
            existingSecret != null &&
            (secret.equals(existingSecret) || this.passwordEncoder.matches(secret, existingSecret))
        ) {
            return existingSecret;
        }
        return secret.startsWith("{") ? secret : this.passwordEncoder.encode(secret);
    }
}
