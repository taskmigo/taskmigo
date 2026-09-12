package io.taskmigo.language;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/// Computes collision-resistant Language artifact identities without recreating the digest engine per operation.
final class Sha256Fingerprint {

    private static final HexFormat HEX = HexFormat.of();
    private static final ThreadLocal<MessageDigest> DIGEST = ThreadLocal.withInitial(Sha256Fingerprint::newDigest);

    private Sha256Fingerprint() {}

    static String of(String value) {
        MessageDigest digest = DIGEST.get();
        digest.reset();
        return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
