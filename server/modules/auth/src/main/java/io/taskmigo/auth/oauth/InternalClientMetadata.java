package io.taskmigo.auth.oauth;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

/// Identifies Taskmigo-managed machine OAuth clients and their shared API scope.
public final class InternalClientMetadata {

    public static final String API_SCOPE = "taskmigo.api";

    private static final String MANAGED = "taskmigo.internal-client.managed";

    private InternalClientMetadata() {}

    public static ClientSettings settings(boolean requireProofKey, boolean requireAuthorizationConsent) {
        return ClientSettings.builder()
            .requireProofKey(requireProofKey)
            .requireAuthorizationConsent(requireAuthorizationConsent)
            .setting(MANAGED, "v1")
            .build();
    }

    public static boolean isManaged(RegisteredClient client) {
        return "v1".equals(setting(client, MANAGED));
    }

    private static @Nullable Object setting(RegisteredClient client, String name) {
        return client.getClientSettings().getSettings().get(name);
    }
}
