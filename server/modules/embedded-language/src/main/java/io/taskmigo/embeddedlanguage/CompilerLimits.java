package io.taskmigo.embeddedlanguage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/// Contains finite safety limits applied before a program becomes executable.
public record CompilerLimits(
    int maxSourceCharacters,
    int maxTokens,
    int maxSyntaxDepth,
    int maxIrNodes,
    int maxBlockDepth,
    int maxListElements
) {
    public CompilerLimits {
        if (
            maxSourceCharacters <= 0 ||
            maxTokens <= 0 ||
            maxSyntaxDepth <= 0 ||
            maxIrNodes <= 0 ||
            maxBlockDepth <= 0 ||
            maxListElements <= 0
        ) {
            throw new IllegalArgumentException("Embedded Language compiler limits must be positive");
        }
    }

    /// Returns the bounded application defaults.
    public static CompilerLimits defaults() {
        return new CompilerLimits(16_000, 4_096, 40, 500, 40, 100);
    }

    /// Returns the cache identity of this compiler contract.
    public String fingerprint() {
        String text = this.toString();
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
