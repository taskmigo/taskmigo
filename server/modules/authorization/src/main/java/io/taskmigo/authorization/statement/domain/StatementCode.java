package io.taskmigo.authorization.statement.domain;

import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/// Represents the stable machine-readable identity of a Statement.
public final class StatementCode {

    private static final Pattern FORMAT = Pattern.compile("[a-zA-Z0-9_-]{6,255}");

    private final String value;

    private StatementCode(String value) {
        this.value = value;
    }

    /// Validates a Statement code without changing its stable persisted representation.
    public static StatementCode of(@Nullable String value) {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw StatementRuleViolation.invalidCode();
        }
        return new StatementCode(value);
    }

    public String value() {
        return this.value;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof StatementCode code && this.value.equals(code.value));
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
