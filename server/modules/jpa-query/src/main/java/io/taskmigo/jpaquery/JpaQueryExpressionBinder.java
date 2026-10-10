package io.taskmigo.jpaquery;

import io.taskmigo.database.criteria.JpaCriteriaComparison;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.model.QueryExpression;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/// Binds an approved Query expression to one operation-scoped JPA query schema.
public final class JpaQueryExpressionBinder {

    private JpaQueryExpressionBinder() {}

    /// Creates a JPA specification whose references are resolved only through the supplied operation schema.
    public static <E> Specification<E> bind(QueryExpression expression, QuerySchema<E> schema) {
        return (root, query, builder) -> predicate(expression, root, builder, schema);
    }

    private static <E> Predicate predicate(
        QueryExpression expression,
        Root<E> root,
        CriteriaBuilder builder,
        QuerySchema<E> schema
    ) {
        return switch (expression) {
            case QueryExpression.Literal literal when literal.value() instanceof Boolean value -> value
                ? builder.conjunction()
                : builder.disjunction();
            case QueryExpression.Unary unary when unary.operator() == QueryExpression.UnaryOperator.NOT -> builder.not(
                predicate(unary.operand(), root, builder, schema)
            );
            case QueryExpression.Binary binary when (
                binary.operator() == QueryExpression.BinaryOperator.AND
            ) -> builder.and(
                predicate(binary.left(), root, builder, schema),
                predicate(binary.right(), root, builder, schema)
            );
            case QueryExpression.Binary binary when (
                binary.operator() == QueryExpression.BinaryOperator.OR
            ) -> builder.or(
                predicate(binary.left(), root, builder, schema),
                predicate(binary.right(), root, builder, schema)
            );
            case QueryExpression.Binary binary when (
                binary.operator() == QueryExpression.BinaryOperator.CONTAINS
            ) -> contains(binary, root, builder, schema);
            case QueryExpression.Binary binary -> comparison(binary, root, builder, schema);
            default -> throw unsupported("predicate");
        };
    }

    private static <E> Predicate comparison(
        QueryExpression.Binary binary,
        Root<E> root,
        CriteriaBuilder builder,
        QuerySchema<E> schema
    ) {
        if (!isComparison(binary.operator())) {
            throw unsupported("comparison operator");
        }
        boolean leftNull = isNull(binary.left());
        boolean rightNull = isNull(binary.right());
        if (leftNull || rightNull) {
            if (
                binary.operator() != QueryExpression.BinaryOperator.EQUAL &&
                binary.operator() != QueryExpression.BinaryOperator.NOT_EQUAL
            ) {
                throw unsupported("null comparison");
            }
            Expression<?> value = value(leftNull ? binary.right() : binary.left(), root, builder, schema);
            return binary.operator() == QueryExpression.BinaryOperator.EQUAL ? value.isNull() : value.isNotNull();
        }
        Expression<?> firstOperand = comparisonValue(binary.left(), binary.right(), root, builder, schema);
        Expression<?> secondOperand = comparisonValue(binary.right(), binary.left(), root, builder, schema);
        Class<?> type = comparisonType(binary.left(), binary.right(), schema);
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

    private static <E> Predicate contains(
        QueryExpression.Binary binary,
        Root<E> root,
        CriteriaBuilder builder,
        QuerySchema<E> schema
    ) {
        if (
            !(binary.left() instanceof QueryExpression.Reference reference) ||
            !(binary.right() instanceof QueryExpression.Literal literal) ||
            !(literal.value() instanceof String text)
        ) {
            throw unsupported("contains operands");
        }
        Class<?> type = referenceType(reference, schema);
        if (type != String.class) {
            throw unsupported("contains field type");
        }
        Expression<String> field = field(reference, root, schema).as(String.class);
        return builder.like(field, literalContainsPattern(text), '\\');
    }

    static String literalContainsPattern(String value) {
        String escaped = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static boolean isComparison(QueryExpression.BinaryOperator operator) {
        return switch (operator) {
            case EQUAL, NOT_EQUAL, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL -> true;
            default -> false;
        };
    }

    private static <E> Expression<?> comparisonValue(
        QueryExpression expression,
        QueryExpression other,
        Root<E> root,
        CriteriaBuilder builder,
        QuerySchema<E> schema
    ) {
        if (
            expression instanceof QueryExpression.Literal literal &&
            other instanceof QueryExpression.Reference reference
        ) {
            return literal(coerce(literal.value(), referenceType(reference, schema)), builder);
        }
        return value(expression, root, builder, schema);
    }

    private static <E> Class<?> comparisonType(QueryExpression left, QueryExpression right, QuerySchema<E> schema) {
        Class<?> type = referenceType(left, schema);
        if (type == null) {
            type = referenceType(right, schema);
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
        throw unsupported("ordered comparison type");
    }

    private static <E> @Nullable Class<?> referenceType(QueryExpression expression, QuerySchema<E> schema) {
        return expression instanceof QueryExpression.Reference reference ? referenceType(reference, schema) : null;
    }

    private static <E> Class<?> referenceType(QueryExpression.Reference reference, QuerySchema<E> schema) {
        return binding(reference, schema).jpaPath().javaType();
    }

    private static <E> Expression<?> value(
        QueryExpression expression,
        Root<E> root,
        CriteriaBuilder builder,
        QuerySchema<E> schema
    ) {
        return switch (expression) {
            case QueryExpression.Reference reference -> field(reference, root, schema);
            case QueryExpression.Literal literal -> literal(literal.value(), builder);
            default -> throw unsupported("value");
        };
    }

    private static <E> Expression<?> field(QueryExpression.Reference reference, Root<E> root, QuerySchema<E> schema) {
        return binding(reference, schema).jpaPath().resolve(root);
    }

    private static <E> QueryField<E, ?> binding(QueryExpression.Reference reference, QuerySchema<E> schema) {
        if (!reference.root().equals("object")) {
            throw unsupported("non-object reference");
        }
        QueryPath path = new QueryPath(reference.path());
        return schema
            .binding(path)
            .orElseThrow(() -> new IllegalArgumentException("Persistence path is not bound: " + path.text()));
    }

    private static Expression<?> literal(@Nullable Object value, CriteriaBuilder builder) {
        return value == null ? builder.nullLiteral(Object.class) : builder.literal(value);
    }

    private static boolean isNull(QueryExpression expression) {
        return expression instanceof QueryExpression.Literal literal && literal.value() == null;
    }

    private static @Nullable Object literalValue(QueryExpression expression) {
        return expression instanceof QueryExpression.Literal literal ? literal.value() : null;
    }

    private static @Nullable Object coerce(@Nullable Object value, Class<?> type) {
        if (value == null || type.isInstance(value)) {
            return value;
        }
        return switch (value) {
            case String text when type == UUID.class -> UUID.fromString(text);
            case String text when type == Instant.class -> Instant.parse(text);
            case String text when type.isEnum() -> enumValue(type, text);
            case Number number when type == Integer.class || type == int.class -> number.intValue();
            case Number number when type == Long.class || type == long.class -> number.longValue();
            case Number number when type == Double.class || type == double.class -> number.doubleValue();
            case Number number when type == Float.class || type == float.class -> number.floatValue();
            default -> throw new IllegalArgumentException("Predicate value has incompatible persistence type");
        };
    }

    private static Object enumValue(Class<?> type, String value) {
        Object[] constants = type.getEnumConstants();
        if (constants != null) {
            for (Object constant : constants) {
                if (((Enum<?>) constant).name().equals(value)) {
                    return constant;
                }
            }
        }
        throw new IllegalArgumentException("Predicate enum value is not valid for " + type.getSimpleName());
    }

    private static IllegalArgumentException unsupported(String kind) {
        return new IllegalArgumentException("Predicate cannot be bound as a JPA " + kind);
    }
}
