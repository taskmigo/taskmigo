package io.taskmigo.identity.persistence.query;

import io.taskmigo.authorization.object.persistence.ObjectAuthorizationExpression;
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

/// Binds the Object Authorization-owned persistence-neutral expression model to JPA Criteria.
final class JpaObjectAuthorizationExpressionBinder {

    private JpaObjectAuthorizationExpressionBinder() {}

    static <E> Specification<E> bind(
        ObjectAuthorizationExpression expression,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        return (root, query, builder) -> predicate(expression, root, builder, paths, types);
    }

    private static <E> Predicate predicate(
        ObjectAuthorizationExpression expression,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        return switch (expression) {
            case ObjectAuthorizationExpression.Literal literal when literal.value() instanceof Boolean value -> value
                ? builder.conjunction()
                : builder.disjunction();
            case ObjectAuthorizationExpression.Unary unary when (
                unary.operator() == ObjectAuthorizationExpression.UnaryOperator.NOT
            ) -> builder.not(predicate(unary.operand(), root, builder, paths, types));
            case ObjectAuthorizationExpression.Binary binary when (
                binary.operator() == ObjectAuthorizationExpression.BinaryOperator.AND
            ) -> builder.and(
                predicate(binary.left(), root, builder, paths, types),
                predicate(binary.right(), root, builder, paths, types)
            );
            case ObjectAuthorizationExpression.Binary binary when (
                binary.operator() == ObjectAuthorizationExpression.BinaryOperator.OR
            ) -> builder.or(
                predicate(binary.left(), root, builder, paths, types),
                predicate(binary.right(), root, builder, paths, types)
            );
            case ObjectAuthorizationExpression.Binary binary when (
                binary.operator() == ObjectAuthorizationExpression.BinaryOperator.IN
            ) -> in(binary, root, builder, paths, types);
            case ObjectAuthorizationExpression.Binary binary -> comparison(binary, root, builder, paths, types);
            default -> throw unsupported("predicate");
        };
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <E> Predicate comparison(
        ObjectAuthorizationExpression.Binary binary,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        boolean leftNull = isNull(binary.left());
        boolean rightNull = isNull(binary.right());
        if (leftNull || rightNull) {
            if (
                binary.operator() != ObjectAuthorizationExpression.BinaryOperator.EQUAL &&
                binary.operator() != ObjectAuthorizationExpression.BinaryOperator.NOT_EQUAL
            ) {
                throw unsupported("null comparison");
            }
            Expression<?> value = value(leftNull ? binary.right() : binary.left(), root, builder, paths, types);
            return binary.operator() == ObjectAuthorizationExpression.BinaryOperator.EQUAL
                ? value.isNull()
                : value.isNotNull();
        }
        Expression<?> firstOperand = comparisonValue(binary.left(), binary.right(), root, builder, paths, types);
        Expression<?> secondOperand = comparisonValue(binary.right(), binary.left(), root, builder, paths, types);
        return switch (binary.operator()) {
            case EQUAL -> builder.equal(firstOperand, secondOperand);
            case NOT_EQUAL -> builder.notEqual(firstOperand, secondOperand);
            case GREATER -> builder.greaterThan((Expression) firstOperand, (Expression) secondOperand);
            case GREATER_OR_EQUAL -> builder.greaterThanOrEqualTo(
                (Expression) firstOperand,
                (Expression) secondOperand
            );
            case LESS -> builder.lessThan((Expression) firstOperand, (Expression) secondOperand);
            case LESS_OR_EQUAL -> builder.lessThanOrEqualTo((Expression) firstOperand, (Expression) secondOperand);
            default -> throw unsupported("comparison operator");
        };
    }

    private static <E> Predicate in(
        ObjectAuthorizationExpression.Binary binary,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        Expression<?> left = value(binary.left(), root, builder, paths, types);
        List<Expression<?>> candidates = new ArrayList<>();
        String logical =
            binary.left() instanceof ObjectAuthorizationExpression.Reference reference
                ? String.join(".", reference.path())
                : null;
        Class<?> type = logical == null ? null : types.get(logical);
        switch (binary.right()) {
            case ObjectAuthorizationExpression.ListValue list -> list.values().forEach(item ->
                candidates.add(
                    item instanceof ObjectAuthorizationExpression.Literal literal
                        ? literal(coerce(literal.value(), type), builder)
                        : value(item, root, builder, paths, types)
                )
            );
            case ObjectAuthorizationExpression.Literal literal when (
                literal.value() instanceof List<?> values
            ) -> values.forEach(item -> candidates.add(literal(coerce(item, type), builder)));
            default -> throw unsupported("IN values");
        }
        return left.in(candidates.toArray(Expression<?>[]::new));
    }

    private static <E> Expression<?> comparisonValue(
        ObjectAuthorizationExpression expression,
        ObjectAuthorizationExpression other,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        if (
            expression instanceof ObjectAuthorizationExpression.Literal literal &&
            other instanceof ObjectAuthorizationExpression.Reference reference
        ) {
            return literal(coerce(literal.value(), types.get(String.join(".", reference.path()))), builder);
        }
        return value(expression, root, builder, paths, types);
    }

    private static <E> Expression<?> value(
        ObjectAuthorizationExpression expression,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        return switch (expression) {
            case ObjectAuthorizationExpression.Reference reference -> field(reference, root, paths);
            case ObjectAuthorizationExpression.Literal literal -> literal(literal.value(), builder);
            case ObjectAuthorizationExpression.ListValue _ -> throw unsupported("list value");
            case ObjectAuthorizationExpression.Binary binary when (
                binary.operator() == ObjectAuthorizationExpression.BinaryOperator.ADD
            ) -> builder.sum(
                numeric(binary.left(), root, builder, paths, types),
                numeric(binary.right(), root, builder, paths, types)
            );
            case ObjectAuthorizationExpression.Binary binary when (
                binary.operator() == ObjectAuthorizationExpression.BinaryOperator.SUBTRACT
            ) -> builder.diff(
                numeric(binary.left(), root, builder, paths, types),
                numeric(binary.right(), root, builder, paths, types)
            );
            case ObjectAuthorizationExpression.Binary binary when (
                binary.operator() == ObjectAuthorizationExpression.BinaryOperator.MULTIPLY
            ) -> builder.prod(
                numeric(binary.left(), root, builder, paths, types),
                numeric(binary.right(), root, builder, paths, types)
            );
            case ObjectAuthorizationExpression.Binary binary when (
                binary.operator() == ObjectAuthorizationExpression.BinaryOperator.DIVIDE
            ) -> builder.quot(
                numeric(binary.left(), root, builder, paths, types),
                numeric(binary.right(), root, builder, paths, types)
            );
            case ObjectAuthorizationExpression.Unary unary when (
                unary.operator() == ObjectAuthorizationExpression.UnaryOperator.MINUS
            ) -> builder.neg(numeric(unary.operand(), root, builder, paths, types));
            default -> throw unsupported("value");
        };
    }

    private static <E> Expression<Number> numeric(
        ObjectAuthorizationExpression expression,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        return value(expression, root, builder, paths, types).as(Number.class);
    }

    private static <E> Expression<?> field(
        ObjectAuthorizationExpression.Reference reference,
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

    private static boolean isNull(ObjectAuthorizationExpression expression) {
        return expression instanceof ObjectAuthorizationExpression.Literal literal && literal.value() == null;
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
