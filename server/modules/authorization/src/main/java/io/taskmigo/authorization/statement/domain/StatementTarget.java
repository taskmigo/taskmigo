package io.taskmigo.authorization.statement.domain;

import org.jspecify.annotations.Nullable;

/// Represents the structurally valid API target persisted by a Statement.
///
/// Regex compilation and target applicability are authorization-runtime concerns and are deliberately not validated here.
public record StatementTarget(String method, String path) {
    private static final int MAX_METHOD_LENGTH = 16;
    private static final int MAX_PATH_LENGTH = 2000;

    public StatementTarget {
        if (method.isBlank()) {
            throw StatementRuleViolation.nonBlank("target.api.method");
        }
        if (path.isBlank()) {
            throw StatementRuleViolation.nonBlank("target.api.path");
        }
        method = method.trim();
        path = path.trim();
        if (method.length() > MAX_METHOD_LENGTH) {
            throw StatementRuleViolation.tooLong("target.api.method", MAX_METHOD_LENGTH);
        }
        if (path.length() > MAX_PATH_LENGTH) {
            throw StatementRuleViolation.tooLong("target.api.path", MAX_PATH_LENGTH);
        }
    }

    /// Creates a target while enforcing only canonical structural constraints.
    public static StatementTarget of(@Nullable String method, @Nullable String path) {
        return new StatementTarget(required(method, "target.api.method"), required(path, "target.api.path"));
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw StatementRuleViolation.nonBlank(field);
        }
        return value.trim();
    }
}
