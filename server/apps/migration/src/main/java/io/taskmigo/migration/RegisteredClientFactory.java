package io.taskmigo.migration;

import io.taskmigo.identity.oauth.InternalClientMetadata;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.JwsAlgorithm;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

/// Builds the persistent OAuth registration for migration-managed clients.
@Component
final class RegisteredClientFactory {

    private final PasswordEncoder passwordEncoder;

    RegisteredClientFactory(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    static void validate(String registrationId, MigrationProperties.ManagedClientProperties definition) {
        if (registrationId == null || registrationId.isBlank()) {
            throw new IllegalStateException("Registered client registration id must not be blank");
        }
        OAuth2AuthorizationServerProperties.Registration registration = Objects.requireNonNull(
            definition.getRegistration(),
            "Registration is required: " + registrationId
        );
        String clientId = requiredClientId(registrationId, registration.getClientId());
        if (definition.isAbsent()) {
            if (
                !BrowserClientMetadata.CLIENT_ID.equals(clientId) &&
                !InternalClientMetadata.hasRequiredClientIdPrefix(clientId)
            ) {
                throw new IllegalStateException("Internal client-id must start with internal__: " + clientId);
            }
            return;
        }
        if (BrowserClientMetadata.CLIENT_ID.equals(clientId)) {
            validateBrowser(clientId, registration);
        } else {
            validateInternal(clientId, registration);
        }
    }

    RegisteredClient create(
        String registrationId,
        MigrationProperties.ManagedClientProperties definition,
        @Nullable RegisteredClient existing
    ) {
        OAuth2AuthorizationServerProperties.Registration registration = definition.getRegistration();
        String clientId = requiredClientId(registrationId, registration.getClientId());
        boolean browser = BrowserClientMetadata.CLIENT_ID.equals(clientId);
        String secret = requiredSecret(clientId, registration.getClientSecret());
        RegisteredClient.Builder builder =
            existing == null
                ? RegisteredClient.withId(registrationId).clientId(clientId)
                : RegisteredClient.from(existing).clientId(clientId);

        return builder
            .clientSecret(this.encodedSecret(secret, existing))
            .clientName(
                registration.getClientName() == null || registration.getClientName().isBlank()
                    ? browser
                        ? "Taskmigo browser client"
                        : "Internal " + clientId
                    : registration.getClientName()
            )
            .clientAuthenticationMethods(methods -> {
                methods.clear();
                registration
                    .getClientAuthenticationMethods()
                    .stream()
                    .map(ClientAuthenticationMethod::new)
                    .forEach(methods::add);
            })
            .authorizationGrantTypes(grants -> {
                grants.clear();
                registration
                    .getAuthorizationGrantTypes()
                    .stream()
                    .map(AuthorizationGrantType::new)
                    .forEach(grants::add);
            })
            .redirectUris(uris -> {
                uris.clear();
                uris.addAll(registration.getRedirectUris());
            })
            .postLogoutRedirectUris(uris -> {
                uris.clear();
                uris.addAll(registration.getPostLogoutRedirectUris());
            })
            .scopes(scopes -> {
                scopes.clear();
                scopes.addAll(registration.getScopes());
            })
            .clientSettings(this.clientSettings(definition, browser))
            .tokenSettings(this.tokenSettings(definition.getToken()))
            .build();
    }

    private static ClientSettings clientSettings(
        MigrationProperties.ManagedClientProperties definition,
        boolean browser
    ) {
        ClientSettings.Builder builder = ClientSettings.builder()
            .requireProofKey(definition.isRequireProofKey())
            .requireAuthorizationConsent(definition.isRequireAuthorizationConsent());
        if (definition.getJwkSetUri() != null) {
            builder.jwkSetUrl(definition.getJwkSetUri());
        }
        if (definition.getTokenEndpointAuthenticationSigningAlgorithm() != null) {
            builder.tokenEndpointAuthenticationSigningAlgorithm(
                jwsAlgorithm(definition.getTokenEndpointAuthenticationSigningAlgorithm())
            );
        }
        builder.setting(browser ? "taskmigo.browser-client.managed" : "taskmigo.internal-client.managed", "v1");
        return builder.build();
    }

    private static TokenSettings tokenSettings(OAuth2AuthorizationServerProperties.Token token) {
        String algorithm = token.getIdTokenSignatureAlgorithm().toUpperCase(Locale.ROOT);
        SignatureAlgorithm signatureAlgorithm = SignatureAlgorithm.from(algorithm);
        if (signatureAlgorithm == null) {
            throw new IllegalStateException("Unknown ID token signature algorithm: " + algorithm);
        }
        return TokenSettings.builder()
            .authorizationCodeTimeToLive(token.getAuthorizationCodeTimeToLive())
            .accessTokenTimeToLive(token.getAccessTokenTimeToLive())
            .accessTokenFormat(new OAuth2TokenFormat(token.getAccessTokenFormat()))
            .deviceCodeTimeToLive(token.getDeviceCodeTimeToLive())
            .reuseRefreshTokens(token.isReuseRefreshTokens())
            .refreshTokenTimeToLive(token.getRefreshTokenTimeToLive())
            .idTokenSignatureAlgorithm(signatureAlgorithm)
            .build();
    }

    private static JwsAlgorithm jwsAlgorithm(String value) {
        String algorithm = value.toUpperCase(Locale.ROOT);
        JwsAlgorithm result = SignatureAlgorithm.from(algorithm);
        if (result == null) {
            result = MacAlgorithm.from(algorithm);
        }
        if (result == null) {
            throw new IllegalStateException("Unknown client authentication signing algorithm: " + algorithm);
        }
        return result;
    }

    private static String requiredSecret(String clientId, @Nullable String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Client secret is required: " + clientId);
        }
        return secret;
    }

    private static String requiredClientId(String registrationId, @Nullable String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("Client id is required: " + registrationId);
        }
        return clientId;
    }

    private static void validateBrowser(
        String clientId,
        OAuth2AuthorizationServerProperties.Registration registration
    ) {
        if (
            !Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue()).equals(
                registration.getClientAuthenticationMethods()
            )
        ) {
            throw new IllegalStateException("Browser client must use only client_secret_basic: " + clientId);
        }
        if (
            !Set.of(
                AuthorizationGrantType.AUTHORIZATION_CODE.getValue(),
                AuthorizationGrantType.REFRESH_TOKEN.getValue()
            ).equals(registration.getAuthorizationGrantTypes())
        ) {
            throw new IllegalStateException(
                "Browser client must use authorization_code and refresh_token: " + clientId
            );
        }
        if (!Set.of("openid", "profile", InternalClientMetadata.API_SCOPE).equals(registration.getScopes())) {
            throw new IllegalStateException("Browser client must use OIDC and Taskmigo API scopes: " + clientId);
        }
        if (registration.getRedirectUris().isEmpty() || registration.getPostLogoutRedirectUris().isEmpty()) {
            throw new IllegalStateException("Browser client redirect URIs are required: " + clientId);
        }
        requiredSecret(clientId, registration.getClientSecret());
    }

    private static void validateInternal(
        String clientId,
        OAuth2AuthorizationServerProperties.Registration registration
    ) {
        if (!InternalClientMetadata.hasRequiredClientIdPrefix(clientId)) {
            throw new IllegalStateException("Internal client-id must start with internal__: " + clientId);
        }
        if (
            !Set.of(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue()).equals(
                registration.getClientAuthenticationMethods()
            )
        ) {
            throw new IllegalStateException("Internal client must use only client_secret_basic: " + clientId);
        }
        if (
            !Set.of(AuthorizationGrantType.CLIENT_CREDENTIALS.getValue()).equals(
                registration.getAuthorizationGrantTypes()
            )
        ) {
            throw new IllegalStateException("Internal client must use only client_credentials: " + clientId);
        }
        if (!Set.of(InternalClientMetadata.API_SCOPE).equals(registration.getScopes())) {
            throw new IllegalStateException("Internal client must use only taskmigo.api scope: " + clientId);
        }
        if (!registration.getRedirectUris().isEmpty() || !registration.getPostLogoutRedirectUris().isEmpty()) {
            throw new IllegalStateException("Internal client must not define redirect URIs: " + clientId);
        }
        requiredSecret(clientId, registration.getClientSecret());
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
