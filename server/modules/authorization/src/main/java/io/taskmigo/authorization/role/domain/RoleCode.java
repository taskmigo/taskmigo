package io.taskmigo.authorization.role.domain;

import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Represents the stable machine-readable identity of a Role.
public final class RoleCode {

    private static final Pattern FORMAT = Pattern.compile("[a-zA-Z0-9_ -]{6,255}");

    private final String value;

    private RoleCode(String value) {
        this.value = value;
    }

    /// Validates a required Role code without changing its persisted identity.
    public static RoleCode of(@Nullable String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw RoleRuleViolation.invalidCode();
        }
        return new RoleCode(value);
    }

    public String value() {
        return this.value;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof RoleCode code && this.value.equals(code.value));
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
