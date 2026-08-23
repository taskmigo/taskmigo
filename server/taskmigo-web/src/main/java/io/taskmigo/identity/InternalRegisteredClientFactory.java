package io.taskmigo.identity;

import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Registration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

@Component
final class InternalRegisteredClientFactory {

    private final PasswordEncoder passwordEncoder;

    InternalRegisteredClientFactory(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    RegisteredClient create(String registrationId, Client client, @Nullable RegisteredClient existing) {
        Registration registration = client.getRegistration();
        String clientId = Objects.requireNonNull(registration.getClientId());
        String secret = validate(clientId, registration);
        RegisteredClient.Builder builder =
            existing == null
                ? RegisteredClient.withId(registrationId).clientId(clientId)
                : RegisteredClient.from(existing);

        return builder
            .clientSecret(encodedSecret(secret, existing))
            .clientName(registration.getClientName() == null ? "Internal " + clientId : registration.getClientName())
            .clientAuthenticationMethods(methods -> {
                methods.clear();
                methods.add(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
            })
            .authorizationGrantTypes(grants -> {
                grants.clear();
                grants.add(AuthorizationGrantType.CLIENT_CREDENTIALS);
            })
            .redirectUris(Set::clear)
            .postLogoutRedirectUris(Set::clear)
            .scopes(scopes -> {
                scopes.clear();
                scopes.add(InternalClientMetadata.API_SCOPE);
            })
            .clientSettings(
                InternalClientMetadata.settings(client.isRequireProofKey(), client.isRequireAuthorizationConsent())
            )
            .tokenSettings(
                TokenSettings.builder()
                    .accessTokenTimeToLive(client.getToken().getAccessTokenTimeToLive())
                    .accessTokenFormat(new OAuth2TokenFormat(client.getToken().getAccessTokenFormat()))
                    .build()
            )
            .build();
    }

    private String validate(String clientId, Registration registration) {
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
        return Objects.requireNonNull(
            registration.getClientSecret(),
            "Internal client secret is required: " + clientId
        );
    }

    private String encodedSecret(String secret, @Nullable RegisteredClient existing) {
        String existingSecret = existing == null ? null : existing.getClientSecret();
        if (
            existingSecret != null && (secret.equals(existingSecret) || passwordEncoder.matches(secret, existingSecret))
        ) {
            return existingSecret;
        }
        return secret.startsWith("{") ? secret : passwordEncoder.encode(secret);
    }
}
