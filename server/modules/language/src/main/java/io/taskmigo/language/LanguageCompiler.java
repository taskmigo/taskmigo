package io.taskmigo.language;

import java.util.Objects;

/// Compiles Taskmigo Language source into a reusable immutable [CompiledSource].
public final class LanguageCompiler {

    private final EmbeddedLanguageCompiler delegate;

    /// Creates a compiler using the finite default Language limits.
    public LanguageCompiler() {
        this(CompilerLimits.defaults());
    }

    /// Creates a compiler using explicit finite Language limits.
    ///
    /// @param limits the compiler resource limits
    public LanguageCompiler(CompilerLimits limits) {
        this.delegate = new EmbeddedLanguageCompiler(Objects.requireNonNull(limits));
    }

    /// Returns the compiler and Language contract identity used by compiled artifacts.
    public String contractFingerprint() {
        return this.delegate.contractFingerprint();
    }

    /// Compiles a statement-bearing program using the fully enabled program profile.
    ///
    /// @param source canonical Language source
    /// @param schema roots and paths visible to the source
    /// @return a reusable compiled source
    public CompiledSource compile(String source, EnvironmentSchema schema) {
        return new CompiledSource(this.delegate.compile(source, schema));
    }

    /// Compiles source using the supplied mode and feature profile.
    ///
    /// @param source canonical Language source
    /// @param schema roots and paths visible to the source
    /// @param profile source mode and enabled feature families
    /// @return a reusable compiled source
    public CompiledSource compile(String source, EnvironmentSchema schema, CompilationProfile profile) {
        return new CompiledSource(this.delegate.compile(source, schema, profile));
    }
}
