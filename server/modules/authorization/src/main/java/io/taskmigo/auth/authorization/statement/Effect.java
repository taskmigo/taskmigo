package io.taskmigo.auth.authorization.statement;

import com.fasterxml.jackson.annotation.JsonCreator;

/// Defines whether a matching authorization Statement grants or denies access.
public enum Effect {
    ALLOW,
    DENY;

    @JsonCreator
    public static Effect from(String value) {
        return switch (value) {
            case "allow" -> ALLOW;
            case "deny" -> DENY;
            default -> throw new IllegalArgumentException("effect must be exactly allow or deny");
        };
    }
}
