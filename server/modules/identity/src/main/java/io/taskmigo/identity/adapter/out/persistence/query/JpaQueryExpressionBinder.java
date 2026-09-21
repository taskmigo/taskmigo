package io.taskmigo.identity.adapter.out.persistence.query;

import io.taskmigo.database.criteria.JpaCriteriaComparison;
import io.taskmigo.query.persistence.QueryExpression;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/// Binds the Query-owned persistence-neutral expression model to JPA Criteria.
final class JpaQueryExpressionBinder {

    private JpaQueryExpressionBinder() {}

    static <E> Specification<E> bind(
        QueryExpression expression,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        return (root, query, builder) -> predicate(expression, root, builder, paths, types);
    }

    private static <E> Predicate predicate(
        QueryExpression expression,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        return switch (expression) {
            case QueryExpression.Literal literal when literal.value() instanceof Boolean value -> value
                ? builder.conjunction()
                : builder.disjunction();
            case QueryExpression.Unary unary when unary.operator() == QueryExpression.UnaryOperator.NOT -> builder.not(
                predicate(unary.operand(), root, builder, paths, types)
            );
            case QueryExpression.Binary binary when (
                binary.operator() == QueryExpression.BinaryOperator.AND
            ) -> builder.and(
                predicate(binary.left(), root, builder, paths, types),
                predicate(binary.right(), root, builder, paths, types)
            );
            case QueryExpression.Binary binary when (
                binary.operator() == QueryExpression.BinaryOperator.OR
            ) -> builder.or(
                predicate(binary.left(), root, builder, paths, types),
                predicate(binary.right(), root, builder, paths, types)
            );
            case QueryExpression.Binary binary when binary.operator() == QueryExpression.BinaryOperator.IN -> in(
                binary,
                root,
                builder,
                paths,
                types
            );
            case QueryExpression.Binary binary -> comparison(binary, root, builder, paths, types);
            default -> throw unsupported("predicate");
        };
    }

    private static <E> Predicate comparison(
        QueryExpression.Binary binary,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        boolean leftNull = isNull(binary.left());
        boolean rightNull = isNull(binary.right());
        if (leftNull || rightNull) {
            if (
                binary.operator() != QueryExpression.BinaryOperator.EQUAL &&
                binary.operator() != QueryExpression.BinaryOperator.NOT_EQUAL
            ) {
                throw unsupported("null comparison");
            }
            Expression<?> value = value(leftNull ? binary.right() : binary.left(), root, builder, paths, types);
            return binary.operator() == QueryExpression.BinaryOperator.EQUAL ? value.isNull() : value.isNotNull();
        }
        Expression<?> firstOperand = comparisonValue(binary.left(), binary.right(), root, builder, paths, types);
        Expression<?> secondOperand = comparisonValue(binary.right(), binary.left(), root, builder, paths, types);
        Class<?> type = comparisonType(binary.left(), binary.right(), types);
        return switch (binary.operator()) {
            case EQUAL -> builder.equal(firstOperand, secondOperand);
            case NOT_EQUAL -> builder.notEqual(firstOperand, secondOperand);
            case GREATER -> JpaCriteriaComparison.greaterThan(builder, firstOperand, secondOperand, type);
            case GREATER_OR_EQUAL -> JpaCriteriaComparison.greaterThanOrEqualTo(
                builder,
                firstOperand,
                secondOperand,
                type
            );
            case LESS -> JpaCriteriaComparison.lessThan(builder, firstOperand, secondOperand, type);
            case LESS_OR_EQUAL -> JpaCriteriaComparison.lessThanOrEqualTo(builder, firstOperand, secondOperand, type);
            default -> throw unsupported("comparison operator");
        };
    }

    private static <E> Predicate in(
        QueryExpression.Binary binary,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        Expression<?> left = value(binary.left(), root, builder, paths, types);
        List<Expression<?>> candidates = new ArrayList<>();
        String logical =
            binary.left() instanceof QueryExpression.Reference reference ? String.join(".", reference.path()) : null;
        Class<?> type = logical == null ? null : types.get(logical);
        switch (binary.right()) {
            case QueryExpression.ListValue list -> list.values().forEach(item ->
                candidates.add(
                    item instanceof QueryExpression.Literal literal
                        ? literal(coerce(literal.value(), type), builder)
                        : value(item, root, builder, paths, types)
                )
            );
            case QueryExpression.Literal literal when literal.value() instanceof List<?> values -> values.forEach(
                item -> candidates.add(literal(coerce(item, type), builder))
            );
            default -> throw unsupported("IN values");
        }
        return left.in(candidates.toArray(Expression<?>[]::new));
    }

    private static Class<?> comparisonType(QueryExpression left, QueryExpression right, Map<String, Class<?>> types) {
        Class<?> type = referenceType(left, types);
        if (type == null) {
            type = referenceType(right, types);
        }
        if (type != null) {
            return type;
        }

        Object literal = literalValue(left);
        if (literal == null) {
            literal = literalValue(right);
        }
        if (literal != null) {
            return literal.getClass();
        }
        if (isNumericExpression(left) || isNumericExpression(right)) {
            return Number.class;
        }
        throw unsupported("ordered comparison type");
    }

    private static @Nullable Class<?> referenceType(QueryExpression expression, Map<String, Class<?>> types) {
        if (!(expression instanceof QueryExpression.Reference reference)) {
            return null;
        }
        String logical = String.join(".", reference.path());
        Class<?> type = types.get(logical);
        if (type == null) {
            throw failure("Persistence type is not bound: " + logical);
        }
        return type;
    }

    private static @Nullable Object literalValue(QueryExpression expression) {
        return expression instanceof QueryExpression.Literal literal ? literal.value() : null;
    }

    private static boolean isNumericExpression(QueryExpression expression) {
        return switch (expression) {
            case QueryExpression.Binary binary -> switch (binary.operator()) {
                case ADD, SUBTRACT, MULTIPLY, DIVIDE -> true;
                default -> false;
            };
            case QueryExpression.Unary unary -> unary.operator() == QueryExpression.UnaryOperator.MINUS;
            default -> false;
        };
    }

    private static <E> Expression<?> comparisonValue(
        QueryExpression expression,
        QueryExpression other,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        if (
            expression instanceof QueryExpression.Literal literal &&
            other instanceof QueryExpression.Reference reference
        ) {
            return literal(coerce(literal.value(), types.get(String.join(".", reference.path()))), builder);
        }
        return value(expression, root, builder, paths, types);
    }

    private static <E> Expression<?> value(
        QueryExpression expression,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        return switch (expression) {
            case QueryExpression.Reference reference -> field(reference, root, paths);
            case QueryExpression.Literal literal -> literal(literal.value(), builder);
            case QueryExpression.ListValue _ -> throw unsupported("list value");
            case QueryExpression.Binary binary when (
                binary.operator() == QueryExpression.BinaryOperator.ADD
            ) -> builder.sum(
                numeric(binary.left(), root, builder, paths, types),
                numeric(binary.right(), root, builder, paths, types)
            );
            case QueryExpression.Binary binary when (
                binary.operator() == QueryExpression.BinaryOperator.SUBTRACT
            ) -> builder.diff(
                numeric(binary.left(), root, builder, paths, types),
                numeric(binary.right(), root, builder, paths, types)
            );
            case QueryExpression.Binary binary when (
                binary.operator() == QueryExpression.BinaryOperator.MULTIPLY
            ) -> builder.prod(
                numeric(binary.left(), root, builder, paths, types),
                numeric(binary.right(), root, builder, paths, types)
            );
            case QueryExpression.Binary binary when (
                binary.operator() == QueryExpression.BinaryOperator.DIVIDE
            ) -> builder.quot(
                numeric(binary.left(), root, builder, paths, types),
                numeric(binary.right(), root, builder, paths, types)
            );
            case QueryExpression.Unary unary when (
                unary.operator() == QueryExpression.UnaryOperator.MINUS
            ) -> builder.neg(numeric(unary.operand(), root, builder, paths, types));
            default -> throw unsupported("value");
        };
    }

    private static <E> Expression<Number> numeric(
        QueryExpression expression,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        return value(expression, root, builder, paths, types).as(Number.class);
    }

    private static <E> Expression<?> field(
        QueryExpression.Reference reference,
        Root<E> root,
        Map<String, String> paths
    ) {
        if (!reference.root().equals("object")) {
            throw unsupported("non-object reference");
        }
        String logical = String.join(".", reference.path());
        String physical = paths.get(logical);
        if (physical == null) {
            throw failure("Persistence path is not bound: " + logical);
        }
        Path<?> current = root;
        for (String segment : physical.split("\\.")) {
            current = current.get(segment);
        }
        return current;
    }

    private static Expression<?> literal(@Nullable Object value, CriteriaBuilder builder) {
        return value == null ? builder.nullLiteral(Object.class) : builder.literal(value);
    }

    private static boolean isNull(QueryExpression expression) {
        return expression instanceof QueryExpression.Literal literal && literal.value() == null;
    }

    private static @Nullable Object coerce(@Nullable Object value, @Nullable Class<?> type) {
        if (value == null || type == null || type.isInstance(value)) {
            return value;
        }
        if (type == UUID.class && value instanceof String text) {
            return UUID.fromString(text);
        }
        if (value instanceof Number number) {
            if (type == Integer.class || type == int.class) {
                return number.intValue();
            }
            if (type == Long.class || type == long.class) {
                return number.longValue();
            }
            if (type == Double.class || type == double.class) {
                return number.doubleValue();
            }
            if (type == Float.class || type == float.class) {
                return number.floatValue();
            }
        }
        throw failure("Predicate value has incompatible persistence type");
    }

    private static IllegalArgumentException unsupported(String kind) {
        return failure("Predicate cannot be bound as a JPA " + kind);
    }

    private static IllegalArgumentException failure(String message) {
        return new IllegalArgumentException(message);
    }
}
