package io.taskmigo.policy;

import java.util.List;

/// Reports a fail-closed Policy Language compilation or evaluation failure.
public final class PolicyException extends RuntimeException {

    private final List<PolicyDiagnostic> diagnostics;

    public PolicyException(PolicyDiagnostic diagnostic) {
        this(List.of(diagnostic));
    }

    public PolicyException(List<PolicyDiagnostic> diagnostics) {
        super(diagnostics.getFirst().message());
        this.diagnostics = List.copyOf(diagnostics);
    }

    /// Returns the stable diagnostics produced by the failed operation.
    public List<PolicyDiagnostic> diagnostics() {
        return this.diagnostics;
    }

    /// Returns the first diagnostic category.
    public PolicyDiagnostic.Category category() {
        return this.diagnostics.getFirst().category();
    }
}
