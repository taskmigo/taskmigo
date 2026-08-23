package io.taskmigo.identity;

import io.taskmigo.identity.IdentityProperties.InternalClientDefinition;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

@Component
final class InternalRegisteredClientFactory {

    private final PasswordEncoder passwordEncoder;

    InternalRegisteredClientFactory(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    RegisteredClient create(
        String registrationKey,
        InternalClientDefinition definition,
        @Nullable RegisteredClient existing,
        boolean secretRotationAllowed
    ) {
        assertImmutableFields(registrationKey, definition, existing);
        RegisteredClient.Builder builder =
            existing == null
                ? RegisteredClient.withId(UUID.randomUUID().toString()).clientId(definition.clientId())
                : RegisteredClient.from(existing);

        return builder
            .clientSecret(encodedSecret(registrationKey, definition, existing, secretRotationAllowed))
            .clientName("Internal " + registrationKey)
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
                scopes.addAll(definition.scopes());
            })
            .clientSettings(ClientSettings.builder().requireProofKey(false).requireAuthorizationConsent(false).build())
            .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build())
            .build();
    }

    private void assertImmutableFields(
        String registrationKey,
        InternalClientDefinition definition,
        @Nullable RegisteredClient existing
    ) {
        if (existing == null) return;
        if (!existing.getClientId().equals(definition.clientId())) {
            throw new IllegalStateException("client-id is immutable for internal client: " + registrationKey);
        }
        if (!existing.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)) {
            throw new IllegalStateException("Internal service client must remain confidential: " + registrationKey);
        }
    }

    private String encodedSecret(
        String registrationKey,
        InternalClientDefinition definition,
        @Nullable RegisteredClient existing,
        boolean secretRotationAllowed
    ) {
        @Nullable
        String secret = definition.clientSecret();
        if (secret == null) throw new IllegalStateException("Internal client secret must not be null");
        @Nullable
        String existingSecret = existing == null ? null : existing.getClientSecret();
        if (existingSecret == null || secretRotationAllowed) {
            return existingSecret != null && passwordEncoder.matches(secret, existingSecret)
                ? existingSecret
                : passwordEncoder.encode(secret);
        }
        if (!passwordEncoder.matches(secret, existingSecret)) {
            throw new IllegalStateException(
                "client-secret changed without increasing configuration-version: " + registrationKey
            );
        }
        return existingSecret;
    }
}
