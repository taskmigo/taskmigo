package io.taskmigo.language;

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
        return Sha256Fingerprint.of(this.toString());
    }
}
