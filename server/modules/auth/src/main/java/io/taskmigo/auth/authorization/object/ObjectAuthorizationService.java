package io.taskmigo.auth.authorization.object;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.filter.FilterAst;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.policy.PolicyIrPartialEvaluator;
import io.taskmigo.auth.authorization.request.AuthorizationSnapshot;
import io.taskmigo.auth.authorization.request.EffectiveStatementResolver;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.Scope;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Builds database-side object authorization plans from effective object Statements.
@Service
public class ObjectAuthorizationService {

    private final EffectiveStatementResolver statements;
    private final JavaScriptPolicyCompiler policyCompiler;
    private final PolicyIrPartialEvaluator partialEvaluator;
    private final List<AuthorizationObjectQueryDialect> dialects;

    ObjectAuthorizationService(
        EffectiveStatementResolver statements,
        JavaScriptPolicyCompiler policyCompiler,
        PolicyIrPartialEvaluator partialEvaluator,
        List<AuthorizationObjectQueryDialect> dialects
    ) {
        this.statements = statements;
        this.policyCompiler = policyCompiler;
        this.partialEvaluator = partialEvaluator;
        this.dialects = List.copyOf(dialects);
    }

    /// Creates an object predicate after capturing the effective Statements for an operation.
    ///
    /// @param userId the User requesting access
    /// @param method the normalized HTTP method
    /// @param path the query path without a query string
    /// @param roots the principal and request authorization roots
    /// @return a plan suitable for applying before query pagination
    /// @throws AuthorizationException when a matching object policy is not queryable
    @Transactional(readOnly = true)
    public ObjectAuthorizationPlan plan(UUID userId, String method, String path, Map<String, ?> roots) {
        return this.plan(new AuthorizationSnapshot(userId, this.statements.resolve(userId), roots), method, path);
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

        for (StatementInfo statement : snapshot.statements()) {
            if (statement.scope() == Scope.OBJECT && statement.matches(method, path)) {
                FilterAst filter =
                    statement.policy() == null
                        ? new FilterAst(new FilterAst.Literal(true))
                        : this.partialEvaluator.partial(
                              this.policyCompiler.compile(statement.policy(), Scope.OBJECT),
                              snapshot.roots()
                          );
                matched.add(statement);
                (statement.effect() == Effect.DENY ? denies : allows).add(filter.expression());
            }
        }

        FilterAst.Expression predicate = new FilterAst.Binary(
            FilterAst.Operator.AND,
            or(allows),
            new FilterAst.Unary(FilterAst.Operator.NOT, or(denies))
        );
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

    private static FilterAst.Expression or(List<FilterAst.Expression> expressions) {
        if (expressions.isEmpty()) return new FilterAst.Literal(false);
        FilterAst.Expression result = expressions.getFirst();
        for (FilterAst.Expression expression : expressions.subList(1, expressions.size())) {
            result = new FilterAst.Binary(FilterAst.Operator.OR, result, expression);
        }
        return result;
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
            case FilterAst.Literal literal when literal.value() instanceof Boolean value -> value
                ? builder.conjunction()
                : builder.disjunction();
            case FilterAst.Unary unary when unary.operator() == FilterAst.Operator.NOT -> builder.not(
                this.predicate(unary.operand(), root, builder, fields)
            );
            case FilterAst.Unary unary when unary.operator() == FilterAst.Operator.PRESENT -> this.present(
                unary.operand(),
                root,
                fields
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

    private <T> Predicate present(FilterAst.Expression expression, Root<T> root, Map<String, Class<?>> fields) {
        return this.field(expression, root, fields).isNotNull();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private <T> Predicate comparison(
        FilterAst.Binary binary,
        Root<T> root,
        CriteriaBuilder builder,
        Map<String, Class<?>> fields
    ) {
        FilterAst.Field field;
        FilterAst.Literal literal;
        boolean fieldLeft;
        if (binary.left() instanceof FilterAst.Field left && binary.right() instanceof FilterAst.Literal right) {
            field = left;
            literal = right;
            fieldLeft = true;
        } else if (binary.right() instanceof FilterAst.Field right && binary.left() instanceof FilterAst.Literal left) {
            field = right;
            literal = left;
            fieldLeft = false;
        } else throw new AuthorizationException("Object comparison requires one field and one literal");

        Expression<?> fieldExpression = this.field(field, root, fields);
        Class<?> fieldType = fieldType(field, fields);
        if (binary.operator() == FilterAst.Operator.IN || binary.operator() == FilterAst.Operator.NIN) {
            if (!fieldLeft || !(literal.value() instanceof Collection<?> values)) throw new AuthorizationException(
                "Object membership comparison requires a field and a literal collection"
            );
            List<Object> coerced = values
                .stream()
                .map(item -> coerce(item, fieldType))
                .toList();
            Predicate membership = fieldExpression.in(coerced);
            return binary.operator() == FilterAst.Operator.NIN ? builder.not(membership) : membership;
        }
        @Nullable
        Object value = coerce(literal.value(), fieldType);
        if (
            binary.operator() == FilterAst.Operator.CONTAINS ||
            binary.operator() == FilterAst.Operator.STARTS_WITH ||
            binary.operator() == FilterAst.Operator.ENDS_WITH
        ) {
            if (!fieldLeft || !(value instanceof String text) || fields.get(field.name()) != String.class) {
                throw new AuthorizationException("String object filter requires a String field and literal");
            }
            String pattern = switch (binary.operator()) {
                case CONTAINS -> "%" + escapeLike(text) + "%";
                case STARTS_WITH -> escapeLike(text) + "%";
                case ENDS_WITH -> "%" + escapeLike(text);
                default -> throw new AssertionError("not a string operator");
            };
            return builder.like(fieldExpression.as(String.class), builder.literal(pattern), '\\');
        }
        if (value == null) return switch (binary.operator()) {
            case EQ -> fieldExpression.isNull();
            case NE -> fieldExpression.isNotNull();
            default -> throw new AuthorizationException("Null object values only support equality");
        };

        FilterAst.Operator operator = fieldLeft ? binary.operator() : reverse(binary.operator());
        Expression<?> valueExpression = builder.literal(value);
        return switch (operator) {
            case EQ -> builder.equal(fieldExpression, valueExpression);
            case NE -> builder.notEqual(fieldExpression, valueExpression);
            case GT -> builder.greaterThan((Expression) fieldExpression, (Expression) valueExpression);
            case GE -> builder.greaterThanOrEqualTo((Expression) fieldExpression, (Expression) valueExpression);
            case LT -> builder.lessThan((Expression) fieldExpression, (Expression) valueExpression);
            case LE -> builder.lessThanOrEqualTo((Expression) fieldExpression, (Expression) valueExpression);
            default -> throw new AuthorizationException("Unsupported object comparison operator");
        };
    }

    private static FilterAst.Operator reverse(FilterAst.Operator operator) {
        return switch (operator) {
            case GT -> FilterAst.Operator.LT;
            case GE -> FilterAst.Operator.LE;
            case LT -> FilterAst.Operator.GT;
            case LE -> FilterAst.Operator.GE;
            default -> operator;
        };
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

    private static Class<?> fieldType(FilterAst.Field field, Map<String, Class<?>> fields) {
        Class<?> type = fields.get(field.name());
        if (type == null) throw new AuthorizationException("Object field is not queryable: " + field.name());
        return type;
    }

    private static @Nullable Object coerce(@Nullable Object value, Class<?> type) {
        if (value == null || type.isInstance(value)) return value;
        if (type == UUID.class && value instanceof String text) return UUID.fromString(text);
        if (value instanceof Number number) {
            if (type == Integer.class || type == int.class) return number.intValue();
            if (type == Long.class || type == long.class) return number.longValue();
            if (type == Double.class || type == double.class) return number.doubleValue();
            if (type == Float.class || type == float.class) return number.floatValue();
        }
        throw new AuthorizationException("Object authorization value has incompatible type");
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
    }
}
