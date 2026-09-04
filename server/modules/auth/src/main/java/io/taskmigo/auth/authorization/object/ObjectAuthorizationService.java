package io.taskmigo.auth.authorization.object;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.filter.FilterAst;
import io.taskmigo.auth.authorization.policy.PolicyIrPartialEvaluator;
import io.taskmigo.auth.authorization.request.AuthorizationSnapshot;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/// Builds database-side object authorization plans from effective object Statements.
@Service
public class ObjectAuthorizationService {

    private final PolicyIrPartialEvaluator partialEvaluator;
    private final List<AuthorizationObjectQueryDialect> dialects;

    ObjectAuthorizationService(
        PolicyIrPartialEvaluator partialEvaluator,
        List<AuthorizationObjectQueryDialect> dialects
    ) {
        this.partialEvaluator = partialEvaluator;
        this.dialects = List.copyOf(dialects);
    }

    /// Builds an object plan from the effective Statements already captured for an operation.
    ///
    /// @param snapshot the immutable authorization state established for the operation
    /// @param method the normalized HTTP method
    /// @param path the query path without a query string
    /// @return a plan suitable for applying before query pagination
    /// @throws AuthorizationException when a matching object policy is not queryable
    public ObjectAuthorizationPlan plan(AuthorizationSnapshot snapshot, String method, String path) {
        AuthorizationObjectQueryDialect dialect = this.dialect(method, path);
        List<FilterAst.Expression> allows = new ArrayList<>();
        List<FilterAst.Expression> denies = new ArrayList<>();
        List<StatementInfo> matched = new ArrayList<>();

        for (var artifact : snapshot.executableStatements()) {
            StatementInfo statement = artifact.statement();
            if (statement.scope() == Scope.OBJECT && artifact.matches(method, path)) {
                FilterAst filter = this.partialEvaluator.partial(artifact.policy().policy(), snapshot.roots());
                matched.add(statement);
                (statement.effect() == Effect.DENY ? denies : allows).add(filter.expression());
            }
        }

        FilterAst.Expression predicate = FilterAst.and(FilterAst.any(allows), FilterAst.not(FilterAst.any(denies)));
        this.validatePredicate(predicate, dialect);
        return new ObjectAuthorizationPlan(new FilterAst(predicate), List.copyOf(matched), dialect.fields());
    }

    private AuthorizationObjectQueryDialect dialect(String method, String path) {
        return this.dialects
            .stream()
            .filter(candidate -> candidate.method().equalsIgnoreCase(method) && candidate.path().equals(path))
            .findFirst()
            .orElseThrow(() ->
                new AuthorizationException("No object authorization query dialect for " + method + " " + path)
            );
    }

    /// Converts a plan into a JPA Specification that callers apply before pagination.
    ///
    /// @param plan the validated object authorization plan
    /// @param <T> the queried entity type
    /// @return a database-side specification implementing the plan predicate
    public <T> Specification<T> specification(ObjectAuthorizationPlan plan) {
        return (root, query, builder) -> this.predicate(plan.predicate().expression(), root, builder, plan.fields());
    }

    private <T> Predicate predicate(
        FilterAst.Expression expression,
        Root<T> root,
        CriteriaBuilder builder,
        Map<String, Class<?>> fields
    ) {
        return switch (expression) {
            case FilterAst.All ignored -> builder.conjunction();
            case FilterAst.None ignored -> builder.disjunction();
            case FilterAst.Literal literal when literal.value() instanceof Boolean value -> value
                ? builder.conjunction()
                : builder.disjunction();
            case FilterAst.Unary unary when unary.operator() == FilterAst.Operator.NOT -> builder.not(
                this.predicate(unary.operand(), root, builder, fields)
            );
            case FilterAst.Binary binary when binary.operator() == FilterAst.Operator.AND -> builder.and(
                this.predicate(binary.left(), root, builder, fields),
                this.predicate(binary.right(), root, builder, fields)
            );
            case FilterAst.Binary binary when binary.operator() == FilterAst.Operator.OR -> builder.or(
                this.predicate(binary.left(), root, builder, fields),
                this.predicate(binary.right(), root, builder, fields)
            );
            case FilterAst.Binary binary -> this.comparison(binary, root, builder, fields);
            default -> throw new AuthorizationException("Object authorization predicate is not boolean");
        };
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private <T> Predicate comparison(
        FilterAst.Binary binary,
        Root<T> root,
        CriteriaBuilder builder,
        Map<String, Class<?>> fields
    ) {
        boolean leftLiteralNull = binary.left() instanceof FilterAst.Literal literal && literal.value() == null;
        boolean rightLiteralNull = binary.right() instanceof FilterAst.Literal literal && literal.value() == null;
        if (leftLiteralNull || rightLiteralNull) {
            if (
                binary.operator() != FilterAst.Operator.EQ && binary.operator() != FilterAst.Operator.NE
            ) throw new AuthorizationException("Null object values only support equality");
            FilterAst.Expression other = leftLiteralNull ? binary.right() : binary.left();
            Expression<?> expression = this.valueExpression(other, root, builder, fields);
            return binary.operator() == FilterAst.Operator.EQ ? expression.isNull() : expression.isNotNull();
        }

        FilterAst.Expression leftValue = binary.left();
        FilterAst.Expression rightValue = binary.right();
        if (leftValue instanceof FilterAst.Field field && rightValue instanceof FilterAst.Literal literal) {
            rightValue = new FilterAst.Literal(coerce(literal.value(), fields.get(field.name())));
        } else if (rightValue instanceof FilterAst.Field field && leftValue instanceof FilterAst.Literal literal) {
            leftValue = new FilterAst.Literal(coerce(literal.value(), fields.get(field.name())));
        }
        Expression<?> left = this.valueExpression(leftValue, root, builder, fields);
        Expression<?> right = this.valueExpression(rightValue, root, builder, fields);
        FilterAst.Operator operator = binary.operator();
        return switch (operator) {
            case EQ -> builder.equal(left, right);
            case NE -> builder.notEqual(left, right);
            case GT -> builder.greaterThan((Expression) left, (Expression) right);
            case GE -> builder.greaterThanOrEqualTo((Expression) left, (Expression) right);
            case LT -> builder.lessThan((Expression) left, (Expression) right);
            case LE -> builder.lessThanOrEqualTo((Expression) left, (Expression) right);
            default -> throw new AuthorizationException("Unsupported object comparison operator");
        };
    }

    private <T> Expression<?> valueExpression(
        FilterAst.Expression expression,
        Root<T> root,
        CriteriaBuilder builder,
        Map<String, Class<?>> fields
    ) {
        return switch (expression) {
            case FilterAst.Field field -> this.field(field, root, fields);
            case FilterAst.Literal literal -> literal.value() == null
                ? builder.nullLiteral(Object.class)
                : builder.literal(literal.value());
            case FilterAst.Binary binary when binary.operator() == FilterAst.Operator.ADD -> builder.sum(
                (Expression) this.valueExpression(binary.left(), root, builder, fields),
                (Expression) this.valueExpression(binary.right(), root, builder, fields)
            );
            case FilterAst.Binary binary when binary.operator() == FilterAst.Operator.SUBTRACT -> builder.diff(
                (Expression) this.valueExpression(binary.left(), root, builder, fields),
                (Expression) this.valueExpression(binary.right(), root, builder, fields)
            );
            case FilterAst.Binary binary when binary.operator() == FilterAst.Operator.MULTIPLY -> builder.prod(
                (Expression) this.valueExpression(binary.left(), root, builder, fields),
                (Expression) this.valueExpression(binary.right(), root, builder, fields)
            );
            case FilterAst.Binary binary when binary.operator() == FilterAst.Operator.DIVIDE -> builder.quot(
                (Expression) this.valueExpression(binary.left(), root, builder, fields),
                (Expression) this.valueExpression(binary.right(), root, builder, fields)
            );
            case FilterAst.Unary unary when unary.operator() == FilterAst.Operator.NEGATE -> builder.neg(
                (Expression) this.valueExpression(unary.operand(), root, builder, fields)
            );
            default -> throw new AuthorizationException("Object filter expression is not a value");
        };
    }

    private void validate(FilterAst.Expression expression, FilterSchema schema) {
        switch (expression) {
            case FilterAst.All ignored -> {
            }
            case FilterAst.None ignored -> {
            }
            case FilterAst.Literal ignored -> {
            }
            case FilterAst.Field field -> {
                if (!schema.fields().containsKey(field.name())) throw new AuthorizationException(
                    "Object field is not queryable: " + field.name()
                );
            }
            case FilterAst.Unary unary -> {
                requireOperator(unary.operator(), schema);
                if (unary.operator() == FilterAst.Operator.NEGATE) validateNumeric(unary.operand(), schema);
                else this.validate(unary.operand(), schema);
            }
            case FilterAst.Binary binary -> {
                requireOperator(binary.operator(), schema);
                if (isArithmetic(binary.operator())) {
                    validateNumeric(binary.left(), schema);
                    validateNumeric(binary.right(), schema);
                } else {
                    this.validate(binary.left(), schema);
                    this.validate(binary.right(), schema);
                }
            }
        }
    }

    private void validatePredicate(FilterAst.Expression expression, FilterSchema schema) {
        switch (expression) {
            case FilterAst.All ignored -> {
            }
            case FilterAst.None ignored -> {
            }
            case FilterAst.Literal literal -> {
                if (!(literal.value() instanceof Boolean)) throw new AuthorizationException(
                    "Object authorization policy must return a boolean or a database predicate"
                );
            }
            case FilterAst.Unary unary when unary.operator() == FilterAst.Operator.NOT -> {
                requireOperator(unary.operator(), schema);
                this.validatePredicate(unary.operand(), schema);
            }
            case FilterAst.Binary binary when (
                binary.operator() == FilterAst.Operator.AND || binary.operator() == FilterAst.Operator.OR
            ) -> {
                requireOperator(binary.operator(), schema);
                this.validatePredicate(binary.left(), schema);
                this.validatePredicate(binary.right(), schema);
            }
            case FilterAst.Binary binary -> {
                if (
                    isArithmetic(binary.operator()) ||
                    binary.operator() == FilterAst.Operator.NOT ||
                    binary.operator() == FilterAst.Operator.NEGATE
                ) {
                    throw new AuthorizationException(
                        "Object authorization policy must return a boolean or a database predicate"
                    );
                }
                this.validate(binary, schema);
            }
            default -> throw new AuthorizationException(
                "Object authorization policy must return a boolean or a database predicate"
            );
        }
    }

    private static void requireOperator(FilterAst.Operator operator, FilterSchema schema) {
        if (!schema.operators().contains(operator)) throw new AuthorizationException(
            "Object filter operator is not supported by the selected Filter Schema: " + operator
        );
    }

    private void validateNumeric(FilterAst.Expression expression, FilterSchema schema) {
        switch (expression) {
            case FilterAst.Literal literal -> {
                if (!(literal.value() instanceof Number)) throw new AuthorizationException(
                    "Object arithmetic requires numeric values"
                );
            }
            case FilterAst.Field field -> {
                Class<?> type = schema.fields().get(field.name());
                if (type == null || !numeric(type)) throw new AuthorizationException(
                    "Object arithmetic requires numeric fields"
                );
            }
            case FilterAst.Binary binary when isArithmetic(binary.operator()) -> {
                requireOperator(binary.operator(), schema);
                validateNumeric(binary.left(), schema);
                validateNumeric(binary.right(), schema);
            }
            case FilterAst.Unary unary when unary.operator() == FilterAst.Operator.NEGATE -> {
                requireOperator(unary.operator(), schema);
                validateNumeric(unary.operand(), schema);
            }
            default -> throw new AuthorizationException("Object arithmetic requires numeric values");
        }
    }

    private static boolean isArithmetic(FilterAst.Operator operator) {
        return switch (operator) {
            case ADD, SUBTRACT, MULTIPLY, DIVIDE -> true;
            default -> false;
        };
    }

    private static boolean numeric(Class<?> type) {
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

    private <T> Expression<?> field(FilterAst.Expression expression, Root<T> root, Map<String, Class<?>> fields) {
        if (!(expression instanceof FilterAst.Field reference)) throw new AuthorizationException(
            "Object filter expression is not a mapped field"
        );
        if (!fields.containsKey(reference.name())) throw new AuthorizationException(
            "Object field is not queryable: " + reference.name()
        );
        return root.get(reference.name());
    }

    private static @Nullable Object coerce(@Nullable Object value, @Nullable Class<?> type) {
        if (value == null || type == null || type.isInstance(value)) return value;
        if (type == UUID.class && value instanceof String text) return UUID.fromString(text);
        if (value instanceof Number number) {
            if (type == Integer.class || type == int.class) return number.intValue();
            if (type == Long.class || type == long.class) return number.longValue();
            if (type == Double.class || type == double.class) return number.doubleValue();
            if (type == Float.class || type == float.class) return number.floatValue();
        }
        throw new AuthorizationException("Object authorization value has incompatible type");
    }

    /// Describes the residual database filter and Statements that produced it.
    public record ObjectAuthorizationPlan(
        FilterAst predicate,
        List<StatementInfo> matchedStatements,
        Map<String, Class<?>> fields
    ) {
        public ObjectAuthorizationPlan {
            matchedStatements = List.copyOf(matchedStatements);
            fields = Map.copyOf(fields);
        }

        /// Returns whether the composed filter is the explicit no-row Null Object.
        public boolean deniesAll() {
            return this.predicate.expression() instanceof FilterAst.None;
        }
    }
}
