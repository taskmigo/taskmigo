package io.taskmigo.auth.authorization.statement;

import com.fasterxml.jackson.annotation.JsonCreator;

/// Identifies whether a Statement applies to an HTTP request or queried object.
public enum Scope {
    OBJECT,
    REQUEST;

    @JsonCreator
    public static Scope from(String value) {
        return switch (value) {
            case "request" -> REQUEST;
            case "object" -> OBJECT;
            default -> throw new IllegalArgumentException("scope must be exactly request or object");
        };
    }
}
