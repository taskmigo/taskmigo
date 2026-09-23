package io.taskmigo.migration.adapter.out.oauth;

import io.taskmigo.migration.application.model.ManagedOAuthClient;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

/// Maps Taskmigo ownership metadata to Spring Authorization Server client settings.
final class InternalClientMetadata {

    private static final String MANAGED = "taskmigo.internal-client.managed";
    private static final String OWNERSHIP = "taskmigo.oauth-client.ownership";

    private InternalClientMetadata() {}

    static ClientSettings settings(ManagedOAuthClient client) {
        ClientSettings.Builder settings = ClientSettings.builder()
            .requireProofKey(client.requireProofKey())
            .requireAuthorizationConsent(client.requireAuthorizationConsent())
            .setting(OWNERSHIP, "internal")
            .setting(MANAGED, "v1");
        if (client.jwkSetUri() != null) {
            settings.jwkSetUrl(client.jwkSetUri());
        }
        if (client.tokenEndpointAuthenticationSigningAlgorithm() != null) {
            settings.tokenEndpointAuthenticationSigningAlgorithm(
                Objects.requireNonNull(
                    SignatureAlgorithm.from(client.tokenEndpointAuthenticationSigningAlgorithm()),
                    "Unsupported client token endpoint signing algorithm"
                )
            );
        }
        return settings.build();
    }

    static boolean isManaged(RegisteredClient client) {
        return "internal".equals(setting(client, OWNERSHIP)) && "v1".equals(setting(client, MANAGED));
    }

    private static @Nullable Object setting(RegisteredClient client, String name) {
        return client.getClientSettings().getSettings().get(name);
    }
}
