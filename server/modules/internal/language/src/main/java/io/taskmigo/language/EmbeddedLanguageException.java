package io.taskmigo.language;

import java.io.Serial;
import java.util.List;

/// Reports a fail-closed Embedded Language compilation or evaluation failure.
public final class EmbeddedLanguageException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<LanguageDiagnostic> diagnostics;

    public EmbeddedLanguageException(LanguageDiagnostic diagnostic) {
        this(List.of(diagnostic));
    }

    public EmbeddedLanguageException(List<LanguageDiagnostic> diagnostics) {
        super(diagnostics.getFirst().message());
        this.diagnostics = List.copyOf(diagnostics);
    }

    /// Returns the stable diagnostics produced by the failed operation.
    public List<LanguageDiagnostic> diagnostics() {
        return this.diagnostics;
    }

    /// Returns the first diagnostic category.
    public LanguageDiagnostic.Category category() {
        return this.diagnostics.getFirst().category();
    }
}
