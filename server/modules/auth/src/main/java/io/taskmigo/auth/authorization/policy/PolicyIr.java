package io.taskmigo.auth.authorization.policy;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// Represents the immutable, parser-independent form of a compiled authorization policy.
public record PolicyIr(Expression expression) {
    /// Represents one policy expression.
    public sealed interface Expression
        permits Literal, UndefinedValue, Reference, PropertyAccess, Binary, Unary, Conditional {}

    /// Represents a JavaScript primitive literal.
    public record Literal(@Nullable Object value) implements Expression {}

    /// Represents JavaScript's missing-value sentinel.
    public record UndefinedValue() implements Expression {}

    /// Represents a value under one of the approved authorization roots.
    public record Reference(String root, List<String> path) implements Expression {
        public Reference {
            path = List.copyOf(path);
        }
    }

    /// Represents access to a property on a statically constructed or runtime value.
    public record PropertyAccess(Expression target, String property) implements Expression {}

    /// Represents a binary JavaScript operation supported by authorization policies.
    public record Binary(BinaryOperator operator, Expression left, Expression right) implements Expression {}

    /// Represents a unary JavaScript operation supported by authorization policies.
    public record Unary(UnaryOperator operator, Expression operand) implements Expression {}

    /// Represents a JavaScript conditional expression produced by an `if` statement.
    public record Conditional(Expression condition, Expression whenTrue, Expression whenFalse) implements Expression {}

    /// Supported binary operations.
    public enum BinaryOperator {
        OR,
        AND,
        EQUAL,
        NOT_EQUAL,
        GREATER,
        GREATER_OR_EQUAL,
        LESS,
        LESS_OR_EQUAL,
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        MODULO,
    }

    /// Supported unary operations.
    public enum UnaryOperator {
        NOT,
        PLUS,
        MINUS,
    }
}
