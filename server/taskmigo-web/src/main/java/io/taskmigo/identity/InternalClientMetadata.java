package io.taskmigo.identity;

import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

final class InternalClientMetadata {

    static final String API_SCOPE = "taskmigo.api";

    private static final String MANAGED = "taskmigo.internal-client.managed";

    private InternalClientMetadata() {}

    static ClientSettings settings(boolean requireProofKey, boolean requireAuthorizationConsent) {
        return ClientSettings.builder()
            .requireProofKey(requireProofKey)
            .requireAuthorizationConsent(requireAuthorizationConsent)
            .setting(MANAGED, "v1")
            .build();
    }

    static boolean isManaged(RegisteredClient client) {
        return "v1".equals(setting(client, MANAGED));
    }

    static Set<String> permissions(RegisteredClient client) {
        return isManaged(client) ? ServicePrincipalPermissions.ALL : Set.of();
    }

    private static @Nullable Object setting(RegisteredClient client, String name) {
        return client.getClientSettings().getSettings().get(name);
    }
}
