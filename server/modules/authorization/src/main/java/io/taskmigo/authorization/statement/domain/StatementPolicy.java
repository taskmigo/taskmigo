package io.taskmigo.authorization.statement.domain;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Represents required Statement policy source without performing authorization semantic validation.
public final class StatementPolicy {

    private final String source;

    private StatementPolicy(String source) {
        this.source = source;
    }

    /// Creates policy source while deferring compilation, typing, and queryability validation to authorization runtime.
    public static StatementPolicy of(@Nullable String source) {
        if (source == null || source.isBlank()) {
            throw StatementRuleViolation.nonBlank("policy");
        }
        return new StatementPolicy(source);
    }

    public String source() {
        return this.source;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return this == other || (other instanceof StatementPolicy policy && this.source.equals(policy.source));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.source);
    }

    @Override
    public String toString() {
        return this.source;
    }
}
