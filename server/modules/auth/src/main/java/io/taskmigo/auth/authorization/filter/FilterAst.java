package io.taskmigo.auth.authorization.filter;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// Represents a parser-independent database filter used by authorization and future query filtering.
public record FilterAst(Expression expression) {
    /// Represents one filter expression.
    public sealed interface Expression permits All, None, Literal, Field, Binary, Unary {}

    /// Represents an unconditional match for every object in the queried resource.
    public record All() implements Expression {}

    /// Represents a predicate that matches no objects.
    public record None() implements Expression {}

    /// Represents a parameter-bound filter value.
    public record Literal(@Nullable Object value) implements Expression {
        /// Creates an immutable literal, including immutable collection values.
        public Literal {
            if (value instanceof List<?> list) value = List.copyOf(list);
        }
    }

    /// Represents a field selected from the resource filter schema.
    public record Field(String name) implements Expression {}

    /// Represents a binary filter operation.
    public record Binary(Operator operator, Expression left, Expression right) implements Expression {}

    /// Represents a unary filter operation.
    public record Unary(Operator operator, Expression operand) implements Expression {}

    /// Operators supported by the authorization filter schema.
    public enum Operator {
        AND,
        OR,
        NOT,
        EQ,
        NE,
        GT,
        GE,
        LT,
        LE,
        IN,
        NIN,
        CONTAINS,
        STARTS_WITH,
        ENDS_WITH,
        PRESENT,
    }
}
