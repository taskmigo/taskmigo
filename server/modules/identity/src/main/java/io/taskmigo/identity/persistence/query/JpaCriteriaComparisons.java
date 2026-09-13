package io.taskmigo.identity.persistence.query;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.UUID;

/// Applies ordered comparisons without exposing raw JPA Criteria types.
final class JpaCriteriaComparisons {

    private JpaCriteriaComparisons() {}

    static Predicate ordered(
        CriteriaBuilder builder,
        Expression<?> left,
        Expression<?> right,
        Class<?> type,
        Operator operator
    ) {
        if (type == String.class) {
            return orderedAs(builder, left, right, String.class, operator);
        }
        if (type == UUID.class) {
            return orderedAs(builder, left, right, UUID.class, operator);
        }
        if (type == Integer.class || type == int.class) {
            return orderedAs(builder, left, right, Integer.class, operator);
        }
        if (type == Long.class || type == long.class) {
            return orderedAs(builder, left, right, Long.class, operator);
        }
        if (type == Double.class || type == double.class) {
            return orderedAs(builder, left, right, Double.class, operator);
        }
        if (type == Float.class || type == float.class) {
            return orderedAs(builder, left, right, Float.class, operator);
        }
        if (type == BigDecimal.class) {
            return orderedAs(builder, left, right, BigDecimal.class, operator);
        }
        throw new IllegalArgumentException("Unsupported ordered persistence type: " + type.getName());
    }

    private static <Y extends Comparable<? super Y>> Predicate orderedAs(
        CriteriaBuilder builder,
        Expression<?> left,
        Expression<?> right,
        Class<Y> type,
        Operator operator
    ) {
        Expression<Y> typedLeft = left.as(type);
        Expression<Y> typedRight = right.as(type);
        return switch (operator) {
            case GREATER -> builder.greaterThan(typedLeft, typedRight);
            case GREATER_OR_EQUAL -> builder.greaterThanOrEqualTo(typedLeft, typedRight);
            case LESS -> builder.lessThan(typedLeft, typedRight);
            case LESS_OR_EQUAL -> builder.lessThanOrEqualTo(typedLeft, typedRight);
        };
    }

    enum Operator {
        GREATER,
        GREATER_OR_EQUAL,
        LESS,
        LESS_OR_EQUAL,
    }
}
