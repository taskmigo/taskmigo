package io.taskmigo.auth.authorization.filter;

import java.util.Collection;
import org.jspecify.annotations.Nullable;

/// Represents a parser-independent database filter used by authorization and future query filtering.
public record FilterAst(Expression expression) {
    /// Returns the explicit filter value matching every row.
    public static Expression all() {
        return new All();
    }

    /// Returns the explicit filter value matching no rows.
    public static Expression none() {
        return new None();
    }

    /// Composes alternatives while removing redundant `ALL` and `NONE` branches.
    public static Expression any(Collection<? extends Expression> expressions) {
        if (expressions.stream().anyMatch(expression -> expression instanceof All || isTrue(expression))) return all();
        var useful = expressions
            .stream()
            .filter(expression -> !(expression instanceof None))
            .filter(expression -> !isFalse(expression))
            .distinct()
            .map(expression -> (Expression) expression)
            .toList();
        if (useful.isEmpty()) return none();
        Expression result = useful.getFirst();
        for (Expression expression : useful.subList(1, useful.size())) result = or(result, expression);
        return result;
    }

    /// Negates a filter with null-object simplification.
    public static Expression not(Expression expression) {
        return switch (expression) {
            case All ignored -> none();
            case None ignored -> all();
            case Literal literal when literal.value() instanceof Boolean value -> value ? none() : all();
            case Unary unary when unary.operator() == Operator.NOT -> unary.operand();
            default -> new Unary(Operator.NOT, expression);
        };
    }

    /// Conjoins filters with null-object simplification.
    public static Expression and(Expression left, Expression right) {
        if (isFalse(left) || isFalse(right)) return none();
        if (left instanceof None || right instanceof None) return none();
        if (isTrue(left)) return right;
        if (isTrue(right)) return left;
        if (left instanceof All) return right;
        if (right instanceof All) return left;
        return new Binary(Operator.AND, left, right);
    }

    private static Expression or(Expression left, Expression right) {
        if (left instanceof All || right instanceof All) return all();
        if (left instanceof None) return right;
        if (right instanceof None) return left;
        return new Binary(Operator.OR, left, right);
    }

    private static boolean isTrue(Expression expression) {
        return expression instanceof Literal literal && Boolean.TRUE.equals(literal.value());
    }

    private static boolean isFalse(Expression expression) {
        return expression instanceof Literal literal && Boolean.FALSE.equals(literal.value());
    }

    /// Represents one filter expression.
    public sealed interface Expression permits All, None, Literal, Field, Binary, Unary {}

    /// Represents an unconditional match for every object in the queried resource.
    public record All() implements Expression {}

    /// Represents a predicate that matches no objects.
    public record None() implements Expression {}

    /// Represents a parameter-bound filter value.
    public record Literal(@Nullable Object value) implements Expression {}

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
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        LT,
        LE,
        NEGATE,
    }
}
