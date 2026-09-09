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
    int maxSemanticAstNodes,
    int maxBlockDepth,
    int maxListElements,
    int maxQuantifierDepth,
    int maxLambdaDepth
) {
    /// Creates limits using the alpha.5 fields and the alpha.6 defaults for quantifiers.
    public CompilerLimits(
        int maxSourceCharacters,
        int maxTokens,
        int maxSyntaxDepth,
        int maxSemanticAstNodes,
        int maxBlockDepth,
        int maxListElements
    ) {
        this(
            maxSourceCharacters,
            maxTokens,
            maxSyntaxDepth,
            maxSemanticAstNodes,
            maxBlockDepth,
            maxListElements,
            20,
            20
        );
    }

    public CompilerLimits {
        if (
            maxSourceCharacters <= 0 ||
            maxTokens <= 0 ||
            maxSyntaxDepth <= 0 ||
            maxSemanticAstNodes <= 0 ||
            maxBlockDepth <= 0 ||
            maxListElements <= 0 ||
            maxQuantifierDepth <= 0 ||
            maxLambdaDepth <= 0
        ) {
            throw new IllegalArgumentException("Embedded Language compiler limits must be positive");
        }
    }

    /// Returns the bounded application defaults.
    public static CompilerLimits defaults() {
        return new CompilerLimits(16_000, 4_096, 40, 500, 40, 100, 20, 20);
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
