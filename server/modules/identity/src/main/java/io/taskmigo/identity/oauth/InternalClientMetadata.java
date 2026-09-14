package io.taskmigo.identity.oauth;

import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

/// Identifies Taskmigo-managed machine OAuth clients and their shared API scope.
public final class InternalClientMetadata {

    /// Prefix required for every Taskmigo-managed machine client identifier.
    public static final String CLIENT_ID_PREFIX = "internal__";

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

    /// Returns whether a client identifier belongs to the internal machine-client namespace.
    ///
    /// @param clientId the persisted or configured OAuth client identifier
    /// @return `true` when the identifier starts with [CLIENT_ID_PREFIX]
    public static boolean hasRequiredClientIdPrefix(String clientId) {
        return clientId.startsWith(CLIENT_ID_PREFIX);
    }

    private static @Nullable Object setting(RegisteredClient client, String name) {
        return client.getClientSettings().getSettings().get(name);
    }
}
