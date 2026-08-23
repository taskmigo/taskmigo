package io.taskmigo.identity;

import io.taskmigo.identity.InternalClientProperties.Definition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

final class InternalClientMetadata {

    static final String API_SCOPE = "taskmigo.api";

    private static final String PREFIX = "taskmigo.internal-client.";
    private static final String MANAGED = PREFIX + "managed";
    private static final String ENABLED = PREFIX + "enabled";
    private static final String GENERATION = PREFIX + "generation";
    private static final String DEFINITION_HASH = PREFIX + "definition-hash";

    private InternalClientMetadata() {}

    static ClientSettings settings(boolean enabled, long generation, String definitionHash) {
        return ClientSettings.builder()
            .requireProofKey(false)
            .requireAuthorizationConsent(false)
            .setting(MANAGED, true)
            .setting(ENABLED, enabled)
            .setting(GENERATION, generation)
            .setting(DEFINITION_HASH, definitionHash)
            .build();
    }

    static boolean isManaged(RegisteredClient client) {
        return Boolean.TRUE.equals(setting(client, MANAGED));
    }

    static boolean isEnabled(RegisteredClient client) {
        return Boolean.TRUE.equals(setting(client, ENABLED));
    }

    static long generation(RegisteredClient client) {
        Object generation = setting(client, GENERATION);
        if (generation instanceof Number number) return number.longValue();
        throw new IllegalStateException("Internal OAuth client has no valid generation: " + client.getClientId());
    }

    static String definitionHash(RegisteredClient client) {
        Object definitionHash = setting(client, DEFINITION_HASH);
        if (definitionHash instanceof String value) return value;
        throw new IllegalStateException("Internal OAuth client has no definition hash: " + client.getClientId());
    }

    static String definitionHash(Definition definition) {
        String canonical = definition.id() + "\n" + definition.enabled();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static Set<String> permissions(RegisteredClient client) {
        return isManaged(client) ? ServicePrincipalPermissions.ALL : Set.of();
    }

    private static @Nullable Object setting(RegisteredClient client, String name) {
        return client.getClientSettings().getSettings().get(name);
    }
}
