package io.taskmigo.language;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/// Defines the Language source mode and enabled feature families for one compilation.
public record CompilationProfile(CompilationMode mode, Set<CompilationFeature> features) {
    private static final CompilationProfile PROGRAM = new CompilationProfile(
        CompilationMode.PROGRAM,
        EnumSet.allOf(CompilationFeature.class)
    );
    private static final CompilationProfile EXPRESSION = new CompilationProfile(
        CompilationMode.EXPRESSION,
        EnumSet.allOf(CompilationFeature.class)
    );

    /// Creates a profile with an immutable feature set.
    public CompilationProfile {
        Objects.requireNonNull(mode);
        features = Set.copyOf(features);
    }

    /// Returns the fully enabled program profile.
    public static CompilationProfile program() {
        return PROGRAM;
    }

    /// Returns the fully enabled expression profile.
    public static CompilationProfile expression() {
        return EXPRESSION;
    }

    /// Returns whether this profile enables the supplied feature family.
    public boolean enables(CompilationFeature feature) {
        return this.features.contains(feature);
    }

    /// Returns a deterministic identity for compiled-artifact matching.
    public String fingerprint() {
        return this.mode + ":" + this.features.stream().map(Enum::name).sorted().toList();
    }
}
