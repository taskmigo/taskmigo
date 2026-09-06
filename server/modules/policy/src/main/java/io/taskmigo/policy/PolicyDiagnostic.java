package io.taskmigo.policy;

/// Describes one stable, source-located Policy Language diagnostic.
public record PolicyDiagnostic(Category category, String message, SourceSpan span) {
    /// Categories required by the Policy Language contract.
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
