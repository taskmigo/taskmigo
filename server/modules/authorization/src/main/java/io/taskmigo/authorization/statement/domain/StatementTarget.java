package io.taskmigo.authorization.statement.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Represents the structurally valid API target persisted by a Statement.
///
/// Regex compilation and target applicability are authorization-runtime concerns and are deliberately not validated here.
public record StatementTarget(String method, String path) {

    private static final int MAX_METHOD_LENGTH = 16;
    private static final int MAX_PATH_LENGTH = 2000;

    /// Creates a target while enforcing only canonical structural constraints.
    public static StatementTarget of(@Nullable String method, @Nullable String path) {
        String normalizedMethod = required(method, "target.api.method");
        String normalizedPath = required(path, "target.api.path");
        if (normalizedMethod.length() > MAX_METHOD_LENGTH) {
            throw StatementRuleViolation.tooLong("target.api.method", MAX_METHOD_LENGTH);
        }
        if (normalizedPath.length() > MAX_PATH_LENGTH) {
            throw StatementRuleViolation.tooLong("target.api.path", MAX_PATH_LENGTH);
        }
        return new StatementTarget(normalizedMethod, normalizedPath);
    }

    private static String required(@Nullable String value, String field) {
        if (value == null || value.isBlank()) {
            throw StatementRuleViolation.nonBlank(field);
        }
        return value.trim();
    }

    public StatementTarget {
        Objects.requireNonNull(method);
        Objects.requireNonNull(path);
    }
}
