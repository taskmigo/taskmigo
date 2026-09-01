package io.taskmigo.project;

import io.taskmigo.authorization.AuthorizationEngine.ObjectPlan;
import io.taskmigo.authorization.AuthorizationExpression;
import io.taskmigo.authorization.AuthorizationExpression.Binary;
import io.taskmigo.authorization.AuthorizationExpression.BinaryOperator;
import io.taskmigo.authorization.AuthorizationExpression.Literal;
import io.taskmigo.authorization.AuthorizationExpression.Reference;
import io.taskmigo.authorization.AuthorizationExpression.Unary;
import io.taskmigo.authorization.AuthorizationExpression.UnaryOperator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class ProjectAclSpecifications {

    private static final Map<String, Class<?>> ATTRIBUTES = Map.of(
        "id",
        UUID.class,
        "organizationId",
        UUID.class,
        "key",
        String.class,
        "name",
        String.class,
        "description",
        String.class,
        "status",
        ProjectStatus.class,
        "archivedAt",
        Instant.class
    );

    private ProjectAclSpecifications() {}

    static void validate(AuthorizationExpression expression) {
        validatePredicate(expression);
    }

    static Specification<ProjectEntity> from(ObjectPlan plan) {
        return (root, query, builder) -> predicate(plan.predicate(), root, builder);
    }

    private static void validatePredicate(AuthorizationExpression expression) {
        switch (expression) {
            case Literal(var value, var ignored) when value instanceof Boolean -> {}
            case Unary(var operator, var operand) when operator == UnaryOperator.NOT -> validatePredicate(operand);
            case Binary(var operator, var left, var right) when operator == BinaryOperator.AND || operator == BinaryOperator.OR -> {
                validatePredicate(left);
                validatePredicate(right);
            }
            case Binary(var operator, var left, var right) when isComparison(operator) -> {
                validateValue(left);
                validateValue(right);
            }
            default -> throw new IllegalArgumentException(
                "Project object authorization cannot compile predicate to a database query: " + expression
            );
        }
    }

    private static void validateValue(AuthorizationExpression expression) {
        switch (expression) {
            case Reference reference when reference.root() == AuthorizationExpression.Root.OBJECT -> attribute(reference);
            case Reference ignored -> {}
            case Literal ignored -> {}
            case Unary(var operator, var operand) when operator == UnaryOperator.NEGATE || operator == UnaryOperator.POSITIVE -> validateValue(operand);
            case Binary(var operator, var left, var right) when isSupportedArithmetic(operator) -> {
                validateValue(left);
                validateValue(right);
            }
            default -> throw new IllegalArgumentException(
                "Project object authorization cannot compile value to a database query: " + expression
            );
        }
    }

    private static boolean isComparison(BinaryOperator operator) {
        return switch (operator) {
            case EQ, NE, GT, GE, LT, LE -> true;
            default -> false;
        };
    }

    private static boolean isSupportedArithmetic(BinaryOperator operator) {
        return switch (operator) {
            case ADD, SUBTRACT, MULTIPLY, DIVIDE -> true;
            default -> false;
        };
    }

    private static Predicate predicate(AuthorizationExpression expression, Root<ProjectEntity> root, CriteriaBuilder builder) {
        return switch (expression) {
            case Literal(var value, var ignored) when value instanceof Boolean bool -> bool
                ? builder.conjunction()
                : builder.disjunction();
            case Unary(var operator, var operand) when operator == UnaryOperator.NOT -> builder.not(
                predicate(operand, root, builder)
            );
            case Binary(var operator, var left, var right) when operator == BinaryOperator.AND -> builder.and(
                predicate(left, root, builder),
                predicate(right, root, builder)
            );
            case Binary(var operator, var left, var right) when operator == BinaryOperator.OR -> builder.or(
                predicate(left, root, builder),
                predicate(right, root, builder)
            );
            case Binary(var operator, var left, var right) -> comparison(operator, left, right, root, builder);
            default -> throw new IllegalArgumentException("Object authorization expression is not a boolean predicate: " + expression);
        };
    }

    private static Predicate comparison(
        BinaryOperator operator,
        AuthorizationExpression left,
        AuthorizationExpression right,
        Root<ProjectEntity> root,
        CriteriaBuilder builder
    ) {
        Expression<?> leftValue = value(left, right, root, builder);
        Expression<?> rightValue = value(right, left, root, builder);
        return switch (operator) {
            case EQ -> builder.equal(leftValue, rightValue);
            case NE -> builder.notEqual(leftValue, rightValue);
            case GT -> ordered(builder, leftValue, rightValue, Ordered.GT);
            case GE -> ordered(builder, leftValue, rightValue, Ordered.GE);
            case LT -> ordered(builder, leftValue, rightValue, Ordered.LT);
            case LE -> ordered(builder, leftValue, rightValue, Ordered.LE);
            default -> throw new IllegalArgumentException("Unsupported boolean project authorization operator: " + operator);
        };
    }

    private static Expression<?> value(
        AuthorizationExpression expression,
        AuthorizationExpression peer,
        Root<ProjectEntity> root,
        CriteriaBuilder builder
    ) {
        return switch (expression) {
            case Reference reference -> root.get(attribute(reference));
            case Literal(var literal, var ignored) -> literal == null
                ? builder.nullLiteral(expectedType(peer))
                : builder.literal(coerce(literal, expectedType(peer)));
            case Unary(var operator, var operand) when operator == UnaryOperator.NEGATE -> builder.neg(
                number(value(operand, peer, root, builder))
            );
            case Unary(var operator, var operand) when operator == UnaryOperator.POSITIVE -> number(
                value(operand, peer, root, builder)
            );
            case Binary(var operator, var left, var right) -> arithmetic(
                operator,
                value(left, right, root, builder),
                value(right, left, root, builder),
                builder
            );
            default -> throw new IllegalArgumentException("Unsupported project authorization value expression: " + expression);
        };
    }

    private static Expression<? extends Number> arithmetic(
        BinaryOperator operator,
        Expression<?> left,
        Expression<?> right,
        CriteriaBuilder builder
    ) {
        Expression<? extends Number> leftNumber = number(left);
        Expression<? extends Number> rightNumber = number(right);
        return switch (operator) {
            case ADD -> builder.sum(leftNumber, rightNumber);
            case SUBTRACT -> builder.diff(leftNumber, rightNumber);
            case MULTIPLY -> builder.prod(leftNumber, rightNumber);
            case DIVIDE -> builder.quot(leftNumber, rightNumber);
            default -> throw new IllegalArgumentException("Unsupported project authorization arithmetic operator: " + operator);
        };
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Predicate ordered(
        CriteriaBuilder builder,
        Expression<?> left,
        Expression<?> right,
        Ordered operator
    ) {
        Expression<? extends Comparable> comparableLeft = (Expression<? extends Comparable>) left;
        Expression<? extends Comparable> comparableRight = (Expression<? extends Comparable>) right;
        return switch (operator) {
            case GT -> builder.greaterThan((Expression) comparableLeft, (Expression) comparableRight);
            case GE -> builder.greaterThanOrEqualTo((Expression) comparableLeft, (Expression) comparableRight);
            case LT -> builder.lessThan((Expression) comparableLeft, (Expression) comparableRight);
            case LE -> builder.lessThanOrEqualTo((Expression) comparableLeft, (Expression) comparableRight);
        };
    }

    private static Expression<? extends Number> number(Expression<?> expression) {
        return expression.as(Number.class);
    }

    private static String attribute(Reference reference) {
        if (reference.root() != AuthorizationExpression.Root.OBJECT || reference.path().size() != 1) {
            throw new IllegalArgumentException("Project authorization supports direct object fields only: " + reference.canonicalPath());
        }
        String attribute = reference.path().getFirst();
        if (!ATTRIBUTES.containsKey(attribute)) throw new IllegalArgumentException(
            "Unknown project authorization field: " + attribute
        );
        return attribute;
    }

    private static Class<?> expectedType(AuthorizationExpression expression) {
        if (expression instanceof Reference reference) return ATTRIBUTES.get(attribute(reference));
        return Object.class;
    }

    private static Object coerce(Object value, Class<?> expected) {
        if (expected == Object.class || expected.isInstance(value)) return value;
        if (expected == UUID.class && value instanceof String text) return UUID.fromString(text);
        if (expected == Instant.class && value instanceof String text) return Instant.parse(text);
        if (expected == ProjectStatus.class && value instanceof String text) return ProjectStatus.valueOf(text);
        throw new IllegalArgumentException(
            "Authorization value " + value + " cannot be converted to project field type " + expected.getSimpleName()
        );
    }

    private enum Ordered {
        GT,
        GE,
        LT,
        LE,
    }
}
