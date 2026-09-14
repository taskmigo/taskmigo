package io.taskmigo.migration;

import io.taskmigo.identity.oauth.InternalClientMetadata;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

final class BrowserClientMetadata {

    static final String CLIENT_ID = "taskmigo-client";
    static final String API_SCOPE = InternalClientMetadata.API_SCOPE;

    private static final String MANAGED = "taskmigo.browser-client.managed";

    private BrowserClientMetadata() {}

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

    private static @Nullable Object setting(RegisteredClient client, String name) {
        return client.getClientSettings().getSettings().get(name);
    }
}
