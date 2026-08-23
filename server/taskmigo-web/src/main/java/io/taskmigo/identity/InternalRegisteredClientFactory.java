package io.taskmigo.identity;

import io.taskmigo.identity.InternalClientProperties.Definition;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

@Component
final class InternalRegisteredClientFactory {

    private final PasswordEncoder passwordEncoder;

    InternalRegisteredClientFactory(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    RegisteredClient create(
        Definition definition,
        @Nullable RegisteredClient existing,
        boolean secretRotationAllowed,
        String definitionHash
    ) {
        RegisteredClient.Builder builder =
            existing == null
                ? RegisteredClient.withId(UUID.randomUUID().toString()).clientId(definition.id())
                : RegisteredClient.from(existing);

        return builder
            .clientSecret(encodedSecret(definition, existing, secretRotationAllowed))
            .clientName("Internal " + definition.id())
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
                InternalClientMetadata.settings(definition.enabled(), definition.generation(), definitionHash)
            )
            .tokenSettings(TokenSettings.builder().reuseRefreshTokens(false).build())
            .build();
    }

    private String encodedSecret(
        Definition definition,
        @Nullable RegisteredClient existing,
        boolean secretRotationAllowed
    ) {
        String secret = definition.secret();
        @Nullable
        String existingSecret = existing == null ? null : existing.getClientSecret();
        if (existingSecret == null || secretRotationAllowed) {
            return existingSecret != null && passwordEncoder.matches(secret, existingSecret)
                ? existingSecret
                : passwordEncoder.encode(secret);
        }
        if (!passwordEncoder.matches(secret, existingSecret)) {
            throw new IllegalStateException("secret changed without increasing generation: " + definition.id());
        }
        return existingSecret;
    }
}
