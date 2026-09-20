package io.taskmigo.identity.group.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Represents the stable, normalized machine-readable identity of a Group.
public final class GroupCode {

    private final String value;

    private GroupCode(String value) {
        this.value = value;
    }

    /// Normalizes a required Group code by trimming surrounding whitespace.
    public static GroupCode of(@Nullable String value) {
        if (value == null || value.isBlank()) {
            throw GroupRuleViolation.required("code");
        }
        return new GroupCode(value.trim());
    }

    public String value() {
        return this.value;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof GroupCode code && this.value.equals(code.value));
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
