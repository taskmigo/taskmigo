package io.taskmigo.migration.adapter.in.installation;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.security.oauth2.server.authorization.autoconfigure.servlet.OAuth2AuthorizationServerProperties.Client;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

/// Identifies OAuth clients managed by the migration application.
final class InternalClientMetadata {

    private static final String MANAGED = "taskmigo.internal-client.managed";
    private static final String OWNERSHIP = "taskmigo.oauth-client.ownership";

    private InternalClientMetadata() {}

    static ClientSettings settings(Client client) {
        ClientSettings.Builder settings = ClientSettings.builder()
            .requireProofKey(client.isRequireProofKey())
            .requireAuthorizationConsent(client.isRequireAuthorizationConsent())
            .setting(OWNERSHIP, "internal")
            .setting(MANAGED, "v1");
        if (client.getJwkSetUri() != null) {
            settings.jwkSetUrl(client.getJwkSetUri());
        }
        if (client.getTokenEndpointAuthenticationSigningAlgorithm() != null) {
            settings.tokenEndpointAuthenticationSigningAlgorithm(
                Objects.requireNonNull(
                    SignatureAlgorithm.from(
                        Objects.requireNonNull(client.getTokenEndpointAuthenticationSigningAlgorithm())
                    ),
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
