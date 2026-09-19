package io.taskmigo.migration;

import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Registration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

/// Builds a migration-managed registered client from Spring Authorization Server properties.
@Component
final class InternalRegisteredClientFactory {

    private final PasswordEncoder passwordEncoder;

    InternalRegisteredClientFactory(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    RegisteredClient create(String registrationId, Client client, @Nullable RegisteredClient existing) {
        Registration registration = client.getRegistration();
        String clientId = Objects.requireNonNull(registration.getClientId(), "Client id is required");
        String secret = Objects.requireNonNull(registration.getClientSecret(), "Client secret is required");
        if (registration.getClientAuthenticationMethods().isEmpty()) {
            throw new IllegalStateException("Client authentication methods are required: " + clientId);
        }
        if (registration.getAuthorizationGrantTypes().isEmpty()) {
            throw new IllegalStateException("Authorization grant types are required: " + clientId);
        }

        RegisteredClient.Builder builder =
            existing == null
                ? RegisteredClient.withId(registrationId).clientId(clientId)
                : RegisteredClient.from(existing).clientId(clientId);

        return builder
            .clientSecret(this.encodedSecret(secret, existing))
            .clientName(registration.getClientName() == null ? clientId : registration.getClientName())
            .clientAuthenticationMethods(methods -> {
                methods.clear();
                methods.addAll(
                    registration
                        .getClientAuthenticationMethods()
                        .stream()
                        .map(ClientAuthenticationMethod::new)
                        .collect(Collectors.toSet())
                );
            })
            .authorizationGrantTypes(grants -> {
                grants.clear();
                grants.addAll(
                    registration
                        .getAuthorizationGrantTypes()
                        .stream()
                        .map(AuthorizationGrantType::new)
                        .collect(Collectors.toSet())
                );
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
            .clientSettings(InternalClientMetadata.settings(client))
            .tokenSettings(tokenSettings(client))
            .build();
    }

    private String encodedSecret(String rawSecret, @Nullable RegisteredClient existing) {
        String existingSecret = existing == null ? null : existing.getClientSecret();
        if (existingSecret != null && this.passwordEncoder.matches(rawSecret, existingSecret)) {
            return existingSecret;
        }
        return this.passwordEncoder.encode(rawSecret);
    }

    private static TokenSettings tokenSettings(Client client) {
        var token = client.getToken();
        return TokenSettings.builder()
            .authorizationCodeTimeToLive(token.getAuthorizationCodeTimeToLive())
            .accessTokenTimeToLive(token.getAccessTokenTimeToLive())
            .accessTokenFormat(new OAuth2TokenFormat(token.getAccessTokenFormat()))
            .deviceCodeTimeToLive(token.getDeviceCodeTimeToLive())
            .reuseRefreshTokens(token.isReuseRefreshTokens())
            .refreshTokenTimeToLive(token.getRefreshTokenTimeToLive())
            .idTokenSignatureAlgorithm(
                Objects.requireNonNull(
                    SignatureAlgorithm.from(Objects.requireNonNull(token.getIdTokenSignatureAlgorithm())),
                    "Unsupported ID token signature algorithm"
                )
            )
            .build();
    }
}
