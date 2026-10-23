package io.taskmigo.jpaquery;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/// Builds ordered JPA Criteria comparisons used by the JPA Query expression binder.
final class JpaCriteriaComparison {

    private JpaCriteriaComparison() {}

    static Predicate greaterThan(CriteriaBuilder builder, Expression<?> left, Expression<?> right, Class<?> type) {
        return compare(builder, left, right, type, Operator.GREATER);
    }

    static Predicate greaterThanOrEqualTo(
        CriteriaBuilder builder,
        Expression<?> left,
        Expression<?> right,
        Class<?> type
    ) {
        return compare(builder, left, right, type, Operator.GREATER_OR_EQUAL);
    }

    static Predicate lessThan(CriteriaBuilder builder, Expression<?> left, Expression<?> right, Class<?> type) {
        return compare(builder, left, right, type, Operator.LESS);
    }

    static Predicate lessThanOrEqualTo(
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
        if (type == Instant.class) {
            return compareComparable(builder, left.as(Instant.class), right.as(Instant.class), operator);
        }
        if (type == Character.class || type == char.class) {
            return compareComparable(builder, left.as(Character.class), right.as(Character.class), operator);
        }
        if (isNumber(type)) {
            return compareNumber(builder, left, right, type, operator);
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
        Expression<?> left,
        Expression<?> right,
        Class<?> type,
        Operator operator
    ) {
        if (type == BigDecimal.class) {
            return compareNumber(builder, left.as(BigDecimal.class), right.as(BigDecimal.class), operator);
        }
        if (type == BigInteger.class) {
            return compareNumber(builder, left.as(BigInteger.class), right.as(BigInteger.class), operator);
        }
        if (type == Byte.class || type == byte.class) {
            return compareNumber(builder, left.as(Byte.class), right.as(Byte.class), operator);
        }
        if (type == Short.class || type == short.class) {
            return compareNumber(builder, left.as(Short.class), right.as(Short.class), operator);
        }
        if (type == Integer.class || type == int.class) {
            return compareNumber(builder, left.as(Integer.class), right.as(Integer.class), operator);
        }
        if (type == Long.class || type == long.class) {
            return compareNumber(builder, left.as(Long.class), right.as(Long.class), operator);
        }
        if (type == Float.class || type == float.class) {
            return compareNumber(builder, left.as(Float.class), right.as(Float.class), operator);
        }
        if (type == Double.class || type == double.class) {
            return compareNumber(builder, left.as(Double.class), right.as(Double.class), operator);
        }
        throw new IllegalArgumentException("Unsupported numeric persistence type: " + type.getTypeName());
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
