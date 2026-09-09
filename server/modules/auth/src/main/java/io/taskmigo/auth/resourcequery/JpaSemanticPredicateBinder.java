package io.taskmigo.auth.resourcequery;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.embeddedlanguage.SemanticAst;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/// Implements the trusted logical-to-JPA step owned by resource persistence adapters.
public final class JpaSemanticPredicateBinder {

    private JpaSemanticPredicateBinder() {}

    public static <E> Specification<E> bind(
        SemanticAst.Expression expression,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        return (root, query, builder) -> predicate(expression, root, builder, paths, types);
    }

    private static <E> Predicate predicate(
        SemanticAst.Expression expression,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        return switch (expression) {
            case SemanticAst.Literal literal when literal.value() instanceof Boolean value -> value
                ? builder.conjunction()
                : builder.disjunction();
            case SemanticAst.Unary unary when unary.operator() == SemanticAst.UnaryOperator.NOT -> builder.not(
                predicate(unary.operand(), root, builder, paths, types)
            );
            case SemanticAst.Binary binary when binary.operator() == SemanticAst.BinaryOperator.AND -> builder.and(
                predicate(binary.left(), root, builder, paths, types),
                predicate(binary.right(), root, builder, paths, types)
            );
            case SemanticAst.Binary binary when binary.operator() == SemanticAst.BinaryOperator.OR -> builder.or(
                predicate(binary.left(), root, builder, paths, types),
                predicate(binary.right(), root, builder, paths, types)
            );
            case SemanticAst.Binary binary when binary.operator() == SemanticAst.BinaryOperator.IN -> in(
                binary,
                root,
                builder,
                paths,
                types
            );
            case SemanticAst.Binary binary -> comparison(binary, root, builder, paths, types);
            default -> throw unsupported("predicate");
        };
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static <E> Predicate comparison(
        SemanticAst.Binary binary,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        boolean leftNull = isNull(binary.left());
        boolean rightNull = isNull(binary.right());
        if (leftNull || rightNull) {
            if (
                binary.operator() != SemanticAst.BinaryOperator.EQUAL &&
                binary.operator() != SemanticAst.BinaryOperator.NOT_EQUAL
            ) {
                throw unsupported("null comparison");
            }
            Expression<?> value = value(leftNull ? binary.right() : binary.left(), root, builder, paths, types);
            return binary.operator() == SemanticAst.BinaryOperator.EQUAL ? value.isNull() : value.isNotNull();
        }
        Expression<?> left = comparisonValue(binary.left(), binary.right(), root, builder, paths, types);
        Expression<?> right = comparisonValue(binary.right(), binary.left(), root, builder, paths, types);
        return switch (binary.operator()) {
            case EQUAL -> builder.equal(left, right);
            case NOT_EQUAL -> builder.notEqual(left, right);
            case GREATER -> builder.greaterThan((Expression) left, (Expression) right);
            case GREATER_OR_EQUAL -> builder.greaterThanOrEqualTo((Expression) left, (Expression) right);
            case LESS -> builder.lessThan((Expression) left, (Expression) right);
            case LESS_OR_EQUAL -> builder.lessThanOrEqualTo((Expression) left, (Expression) right);
            default -> throw unsupported("comparison operator");
        };
    }

    private static <E> Predicate in(
        SemanticAst.Binary binary,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        Expression<?> left = value(binary.left(), root, builder, paths, types);
        CriteriaBuilder.In<Object> predicate = builder.in(left);
        String logical =
            binary.left() instanceof SemanticAst.Reference reference ? String.join(".", reference.path()) : null;
        Class<?> type = logical == null ? null : types.get(logical);
        switch (binary.right()) {
            case SemanticAst.ListLiteral list -> list.values().forEach(item ->
                predicate.value(
                    item instanceof SemanticAst.Literal literal
                        ? literal(coerce(literal.value(), type), builder)
                        : value(item, root, builder, paths, types)
                )
            );
            case SemanticAst.Literal literal when literal.value() instanceof List<?> values -> values.forEach(item ->
                predicate.value(literal(coerce(item, type), builder))
            );
            default -> throw unsupported("IN values");
        }
        return predicate;
    }

    private static <E> Expression<?> comparisonValue(
        SemanticAst.Expression expression,
        SemanticAst.Expression other,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        if (expression instanceof SemanticAst.Literal literal && other instanceof SemanticAst.Reference reference) {
            return literal(coerce(literal.value(), types.get(String.join(".", reference.path()))), builder);
        }
        return value(expression, root, builder, paths, types);
    }

    private static <E> Expression<?> value(
        SemanticAst.Expression expression,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        return switch (expression) {
            case SemanticAst.Reference reference -> field(reference, root, paths);
            case SemanticAst.Literal literal -> literal(literal.value(), builder);
            case SemanticAst.ListLiteral _ -> throw unsupported("list value");
            case SemanticAst.Binary binary when binary.operator() == SemanticAst.BinaryOperator.ADD -> builder.sum(
                numeric(binary.left(), root, builder, paths, types),
                numeric(binary.right(), root, builder, paths, types)
            );
            case SemanticAst.Binary binary when (
                binary.operator() == SemanticAst.BinaryOperator.SUBTRACT
            ) -> builder.diff(
                numeric(binary.left(), root, builder, paths, types),
                numeric(binary.right(), root, builder, paths, types)
            );
            case SemanticAst.Binary binary when (
                binary.operator() == SemanticAst.BinaryOperator.MULTIPLY
            ) -> builder.prod(
                numeric(binary.left(), root, builder, paths, types),
                numeric(binary.right(), root, builder, paths, types)
            );
            case SemanticAst.Binary binary when binary.operator() == SemanticAst.BinaryOperator.DIVIDE -> builder.quot(
                numeric(binary.left(), root, builder, paths, types),
                numeric(binary.right(), root, builder, paths, types)
            );
            case SemanticAst.Unary unary when unary.operator() == SemanticAst.UnaryOperator.MINUS -> builder.neg(
                numeric(unary.operand(), root, builder, paths, types)
            );
            default -> throw unsupported("value");
        };
    }

    private static <E> Expression<Number> numeric(
        SemanticAst.Expression expression,
        Root<E> root,
        CriteriaBuilder builder,
        Map<String, String> paths,
        Map<String, Class<?>> types
    ) {
        return value(expression, root, builder, paths, types).as(Number.class);
    }

    private static <E> Expression<?> field(SemanticAst.Reference reference, Root<E> root, Map<String, String> paths) {
        if (!reference.root().equals("object")) {
            throw unsupported("non-object reference");
        }
        String logical = String.join(".", reference.path());
        String physical = paths.get(logical);
        if (physical == null) {
            throw new AuthorizationException("Persistence path is not bound: " + logical);
        }
        Expression<?> current = root;
        for (String segment : physical.split("\\.")) {
            current = ((Path<?>) current).get(segment);
        }
        return current;
    }

    private static Expression<?> literal(@Nullable Object value, CriteriaBuilder builder) {
        return value == null ? builder.nullLiteral(Object.class) : builder.literal(value);
    }

    private static boolean isNull(SemanticAst.Expression expression) {
        return expression instanceof SemanticAst.Literal literal && literal.value() == null;
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
        throw new AuthorizationException("Predicate value has incompatible persistence type");
    }

    private static AuthorizationException unsupported(String kind) {
        return new AuthorizationException("Predicate cannot be bound as a JPA " + kind);
    }
}
