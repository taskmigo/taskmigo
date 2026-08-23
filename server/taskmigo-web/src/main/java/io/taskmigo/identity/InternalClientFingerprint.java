package io.taskmigo.identity;

import io.taskmigo.identity.IdentityProperties.InternalClientDefinition;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

final class InternalClientFingerprint {

    private InternalClientFingerprint() {}

    static String calculate(InternalClientDefinition definition) {
        String canonical = String.join(
            "\n",
            definition.clientId(),
            Boolean.toString(definition.enabled()),
            definition.scopes().stream().sorted().toList().toString(),
            definition.servicePermissions().stream().sorted().toList().toString()
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
