package io.taskmigo.embeddedlanguage;

/// Selects the source entry point used by the Embedded Language compiler.
public enum CompilationMode {
    /// Compiles statements with complete return control flow.
    PROGRAM,
    /// Compiles exactly one expression without a statement wrapper.
    EXPRESSION,
}
