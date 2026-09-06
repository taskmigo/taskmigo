package io.taskmigo.embeddedlanguage;

/// Describes one stable, source-located Embedded Language diagnostic.
public record LanguageDiagnostic(Category category, String message, SourceSpan span) {
    /// Categories required by the Embedded Language contract.
    public enum Category {
        SyntaxError,
        BindingError,
        ControlFlowError,
        TypeError,
        ComplexityError,
        QueryabilityError,
    }

    /// Identifies the source range associated with a diagnostic.
    public record SourceSpan(int startLine, int startColumn, int endLine, int endColumn) {}
}
