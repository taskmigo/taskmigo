package io.taskmigo.authorization.statement;

/// Defines whether a matching authorization Statement grants or denies access.
public enum Effect {
    ALLOW,
    DENY;

    /// Parses the canonical lowercase effect name used by external configuration adapters.
    public static Effect from(String value) {
        return switch (value) {
            case "allow" -> ALLOW;
            case "deny" -> DENY;
            default -> throw new IllegalArgumentException("effect must be exactly allow or deny");
        };
    }
}
