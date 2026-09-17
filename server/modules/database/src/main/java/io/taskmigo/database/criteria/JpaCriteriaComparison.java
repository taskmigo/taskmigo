package io.taskmigo.database.criteria;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.util.UUID;

/// Builds ordered JPA Criteria comparisons while preserving the Java type of
/// each expression.
///
/// This is shared technical infrastructure only: bounded contexts continue to
/// own their resource schemas, logical paths, and entity mappings.
public final class JpaCriteriaComparison {

    private JpaCriteriaComparison() {}

    /// Builds a greater-than predicate for two expressions of the supplied type.
    public static Predicate greaterThan(CriteriaBuilder builder, Expression<?> left, Expression<?> right, Class<?> type) {
        return compare(builder, left, right, type, Operator.GREATER);
    }

    /// Builds a greater-than-or-equal predicate for two expressions of the supplied type.
    public static Predicate greaterThanOrEqualTo(
        CriteriaBuilder builder,
        Expression<?> left,
        Expression<?> right,
        Class<?> type
    ) {
        return compare(builder, left, right, type, Operator.GREATER_OR_EQUAL);
    }

    /// Builds a less-than predicate for two expressions of the supplied type.
    public static Predicate lessThan(CriteriaBuilder builder, Expression<?> left, Expression<?> right, Class<?> type) {
        return compare(builder, left, right, type, Operator.LESS);
    }

    /// Builds a less-than-or-equal predicate for two expressions of the supplied type.
    public static Predicate lessThanOrEqualTo(
        CriteriaBuilder builder,
        Expression<?> left,
        Expression<?> right,
        Class<?> type
    ) {
        return compare(builder, left, right, type, Operator.LESS_OR_EQUAL);
    }

    private static Predicate compare(
        CriteriaBuilder builder,
        Expression<?> left,
        Expression<?> right,
        Class<?> type,
        Operator operator
    ) {
        if (type == String.class) {
            return compareComparable(builder, left.as(String.class), right.as(String.class), operator);
        }
        if (type == UUID.class) {
            return compareComparable(builder, left.as(UUID.class), right.as(UUID.class), operator);
        }
        if (type == Character.class || type == char.class) {
            return compareComparable(builder, left.as(Character.class), right.as(Character.class), operator);
        }
        if (isNumber(type)) {
            return compareNumber(builder, left.as(Number.class), right.as(Number.class), operator);
        }
        throw new IllegalArgumentException("Unsupported ordered persistence type: " + type.getTypeName());
    }

    private static <T extends Comparable<? super T>> Predicate compareComparable(
        CriteriaBuilder builder,
        Expression<T> left,
        Expression<T> right,
        Operator operator
    ) {
        return switch (operator) {
            case GREATER -> builder.greaterThan(left, right);
            case GREATER_OR_EQUAL -> builder.greaterThanOrEqualTo(left, right);
            case LESS -> builder.lessThan(left, right);
            case LESS_OR_EQUAL -> builder.lessThanOrEqualTo(left, right);
        };
    }

    private static Predicate compareNumber(
        CriteriaBuilder builder,
        Expression<? extends Number> left,
        Expression<? extends Number> right,
        Operator operator
    ) {
        return switch (operator) {
            case GREATER -> builder.gt(left, right);
            case GREATER_OR_EQUAL -> builder.ge(left, right);
            case LESS -> builder.lt(left, right);
            case LESS_OR_EQUAL -> builder.le(left, right);
        };
    }

    private static boolean isNumber(Class<?> type) {
        return (
            Number.class.isAssignableFrom(type) ||
            type == byte.class ||
            type == short.class ||
            type == int.class ||
            type == long.class ||
            type == float.class ||
            type == double.class
        );
    }

    private enum Operator {
        GREATER,
        GREATER_OR_EQUAL,
        LESS,
        LESS_OR_EQUAL,
    }
}
