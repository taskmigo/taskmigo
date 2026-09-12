package io.taskmigo.language;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/// Defines the Language source mode and enabled feature families for one compilation.
public final class CompilationProfile {

    private static final CompilationProfile PROGRAM = new CompilationProfile(
        CompilationMode.PROGRAM,
        EnumSet.allOf(CompilationFeature.class)
    );
    private static final CompilationProfile EXPRESSION = new CompilationProfile(
        CompilationMode.EXPRESSION,
        EnumSet.allOf(CompilationFeature.class)
    );

    private final CompilationMode mode;
    private final Set<CompilationFeature> features;
    private final long featureMask;
    private final String fingerprint;

    /// Creates a profile with an immutable feature set and precomputed identity.
    public CompilationProfile(CompilationMode mode, Set<CompilationFeature> features) {
        this.mode = Objects.requireNonNull(mode);
        this.features = Set.copyOf(features);
        long mask = 0L;
        for (CompilationFeature feature : this.features) {
            mask |= 1L << feature.ordinal();
        }
        this.featureMask = mask;
        this.fingerprint = mode + ":" + Long.toUnsignedString(mask, 16);
    }

    /// Returns the fully enabled program profile.
    public static CompilationProfile program() {
        return PROGRAM;
    }

    /// Returns the fully enabled expression profile.
    public static CompilationProfile expression() {
        return EXPRESSION;
    }

    /// Returns the source entry mode.
    public CompilationMode mode() {
        return this.mode;
    }

    /// Returns the immutable enabled feature families.
    public Set<CompilationFeature> features() {
        return this.features;
    }

    /// Returns whether this profile enables the supplied feature family.
    public boolean enables(CompilationFeature feature) {
        return (this.featureMask & (1L << feature.ordinal())) != 0L;
    }

    /// Returns a deterministic precomputed identity for compiled-artifact matching.
    public String fingerprint() {
        return this.fingerprint;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CompilationProfile profile &&
            this.mode == profile.mode &&
            this.featureMask == profile.featureMask;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.mode, this.featureMask);
    }

    @Override
    public String toString() {
        return "CompilationProfile[mode=" + this.mode + ", features=" + this.features + "]";
    }
}
