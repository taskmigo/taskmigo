package io.taskmigo.auth.authorization.statement;

import com.fasterxml.jackson.annotation.JsonCreator;

/// Identifies whether a Statement applies to an HTTP request or queried object.
public enum TargetType {
    OBJECT,
    REQUEST;

    @JsonCreator
    public static TargetType from(String value) {
        return switch (value) {
            case "request" -> REQUEST;
            case "object" -> OBJECT;
            default -> throw new IllegalArgumentException("target.type must be exactly request or object");
        };
    }
}
