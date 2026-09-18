package io.taskmigo.language;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;

/// Computes deterministic Language artifact fingerprints from UTF-8 source text.
final class LanguageFingerprint {

    private LanguageFingerprint() {}

    static String of(String value) {
        return Hashing.sha256().hashString(value, StandardCharsets.UTF_8).toString();
    }
}
