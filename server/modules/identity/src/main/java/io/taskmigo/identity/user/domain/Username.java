package io.taskmigo.identity.user.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Represents a normalized User username.
public final class Username {

    public static final String SYSTEM = "system";

    private final String value;

    private Username(String value) {
        this.value = value;
    }

    /// Normalizes a required username by trimming surrounding whitespace.
    public static Username of(@Nullable String value) {
        if (value == null || value.isBlank()) {
            throw UserRuleViolation.required("username");
        }
        return new Username(value.trim());
    }

    public String value() {
        return this.value;
    }

    /// Returns whether this username identifies Taskmigo's reserved system User.
    public boolean system() {
        return SYSTEM.equals(this.value);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof Username username && this.value.equals(username.value));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.value);
    }

    @Override
    public String toString() {
        return this.value;
    }
}
