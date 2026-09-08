package io.taskmigo.embeddedlanguage;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/// Defines the Embedded Language source mode and enabled feature families for one compilation.
public record CompilationProfile(CompilationMode mode, Set<CompilationFeature> features) {

    /// Creates a profile with an immutable feature set.
    public CompilationProfile {
        Objects.requireNonNull(mode);
        features = Set.copyOf(features);
    }

    /// Returns the fully enabled profile used by the backwards-compatible compiler overload.
    public static CompilationProfile program() {
        return new CompilationProfile(CompilationMode.PROGRAM, EnumSet.allOf(CompilationFeature.class));
    }

    /// Returns a fully enabled expression profile.
    public static CompilationProfile expression() {
        return new CompilationProfile(CompilationMode.EXPRESSION, EnumSet.allOf(CompilationFeature.class));
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
