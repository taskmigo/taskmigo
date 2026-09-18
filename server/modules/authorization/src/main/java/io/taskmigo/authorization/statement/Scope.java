package io.taskmigo.authorization.statement;

/// Identifies whether a Statement applies to an HTTP request or queried object.
public enum Scope {
    OBJECT,
    REQUEST;

    /// Parses the canonical lowercase scope name used by external configuration adapters.
    public static Scope from(String value) {
        return switch (value) {
            case "request" -> REQUEST;
            case "object" -> OBJECT;
            default -> throw new IllegalArgumentException("scope must be exactly request or object");
        };
    }
}
