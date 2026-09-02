package io.taskmigo.auth;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Builds database-side object authorization plans from effective object Statements.
@Service
public class ObjectAuthorizationService {

    private final EffectiveStatementResolver statements;
    private final AuthorizationCompiler compiler;
    private final List<AuthorizationObjectQueryDialect> dialects;

    ObjectAuthorizationService(
        EffectiveStatementResolver statements,
        AuthorizationCompiler compiler,
        List<AuthorizationObjectQueryDialect> dialects
    ) {
        this.statements = statements;
        this.compiler = compiler;
        this.dialects = List.copyOf(dialects);
    }

    /// Creates an object predicate with allow-any and deny-overrides semantics.
    ///
    /// Principal and request references are replaced with literals before the selected resource dialect validates
    /// the predicate. Object references remain in the returned expression for database-side translation.
    ///
    /// @param userId the User requesting access
    /// @param method the normalized HTTP method
    /// @param path the query path without a query string
    /// @param roots the principal, request, and optional object authorization roots
    /// @return a plan suitable for applying before query pagination
    /// @throws AuthorizationException when a matching object condition is not queryable
    @Transactional(readOnly = true)
    public ObjectAuthorizationPlan plan(UUID userId, String method, String path, Map<String, ?> roots) {
        AuthorizationObjectQueryDialect dialect = this.dialect(method, path);
        List<AuthorizationCompiler.Expression> allows = new ArrayList<>();
        List<AuthorizationCompiler.Expression> denies = new ArrayList<>();
        List<StatementService.StatementInfo> matched = new ArrayList<>();

        for (StatementService.StatementInfo statement : this.statements.resolve(userId)) {
            if (statement.target().type() == StatementService.TargetType.OBJECT && statement.matches(method, path)) {
                AuthorizationCompiler.Expression specialized = this.specialize(this.compiler.compile(statement), roots);
                dialect.validate(specialized);
                matched.add(statement);
                (statement.effect() == StatementService.Effect.DENY ? denies : allows).add(specialized);
            }
        }

        AuthorizationCompiler.Expression predicate = new AuthorizationCompiler.Binary(
            AuthorizationCompiler.BinaryOperator.AND,
            or(allows),
            new AuthorizationCompiler.Unary(AuthorizationCompiler.UnaryOperator.NOT, or(denies))
        );
        return new ObjectAuthorizationPlan(predicate, List.copyOf(matched), dialect.fields());
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

    private AuthorizationCompiler.Expression specialize(
        AuthorizationCompiler.Expression expression,
        Map<String, ?> roots
    ) {
        return switch (expression) {
            case AuthorizationCompiler.LiteralValue literal -> literal;
            case AuthorizationCompiler.Reference reference when (
                !reference.root().equals("object")
            ) -> new AuthorizationCompiler.LiteralValue(this.resolve(reference, roots));
            case AuthorizationCompiler.Reference reference -> reference;
            case AuthorizationCompiler.Unary unary -> new AuthorizationCompiler.Unary(
                unary.operator(),
                this.specialize(unary.operand(), roots)
            );
            case AuthorizationCompiler.Binary binary -> new AuthorizationCompiler.Binary(
                binary.operator(),
                this.specialize(binary.left(), roots),
                this.specialize(binary.right(), roots)
            );
        };
    }

    private Object resolve(AuthorizationCompiler.Reference reference, Map<String, ?> roots) {
        Object current = roots.get(reference.root());
        for (String part : reference.path()) {
            if (!(current instanceof Map<?, ?> values) || !values.containsKey(part)) {
                throw new AuthorizationException(
                    "Missing authorization context value: " + reference.root() + "." + part
                );
            }
            current = values.get(part);
            if (current == null) throw new AuthorizationException(
                "Null authorization context value: " + reference.root() + "." + part
            );
        }
        return Objects.requireNonNull(current);
    }

    private static AuthorizationCompiler.Expression or(List<AuthorizationCompiler.Expression> expressions) {
        if (expressions.isEmpty()) return new AuthorizationCompiler.LiteralValue(false);
        AuthorizationCompiler.Expression result = expressions.getFirst();
        for (AuthorizationCompiler.Expression expression : expressions.subList(1, expressions.size())) {
            result = new AuthorizationCompiler.Binary(AuthorizationCompiler.BinaryOperator.OR, result, expression);
        }
        return result;
    }

    /// Converts a plan into a JPA Specification that callers apply before pagination.
    ///
    /// @param plan the validated object authorization plan
    /// @param <T> the queried entity type
    /// @return a database-side specification implementing the plan predicate
    public <T> Specification<T> specification(ObjectAuthorizationPlan plan) {
        return (root, query, builder) -> this.predicate(plan.predicate(), root, builder, plan.fields());
    }

    private <T> Predicate predicate(
        AuthorizationCompiler.Expression expression,
        Root<T> root,
        CriteriaBuilder builder,
        Map<String, Class<?>> fields
    ) {
        return switch (expression) {
            case AuthorizationCompiler.LiteralValue literal when literal.value() instanceof Boolean value -> value
                ? builder.conjunction()
                : builder.disjunction();
            case AuthorizationCompiler.Unary unary when (
                unary.operator() == AuthorizationCompiler.UnaryOperator.NOT
            ) -> builder.not(this.predicate(unary.operand(), root, builder, fields));
            case AuthorizationCompiler.Binary binary when (
                binary.operator() == AuthorizationCompiler.BinaryOperator.AND
            ) -> builder.and(
                this.predicate(binary.left(), root, builder, fields),
                this.predicate(binary.right(), root, builder, fields)
            );
            case AuthorizationCompiler.Binary binary when (
                binary.operator() == AuthorizationCompiler.BinaryOperator.OR
            ) -> builder.or(
                this.predicate(binary.left(), root, builder, fields),
                this.predicate(binary.right(), root, builder, fields)
            );
            case AuthorizationCompiler.Binary binary -> this.comparison(binary, root, builder, fields);
            default -> throw new AuthorizationException("Object authorization predicate is not boolean");
        };
    }

    @SuppressWarnings({ "unchecked", "SuspiciousNameCombination" })
    private <T> Predicate comparison(
        AuthorizationCompiler.Binary binary,
        Root<T> root,
        CriteriaBuilder builder,
        Map<String, Class<?>> fields
    ) {
        Expression<?> left = value(binary.left(), binary.right(), root, builder, fields);
        Expression<?> right = value(binary.right(), binary.left(), root, builder, fields);
        return switch (binary.operator()) {
            case EQUAL -> builder.equal(left, right);
            case NOT_EQUAL -> builder.notEqual(left, right);
            case GREATER -> builder.greaterThan(asComparable(left), asComparable(right));
            case GREATER_OR_EQUAL -> builder.greaterThanOrEqualTo(asComparable(left), asComparable(right));
            case LESS -> builder.lessThan(asComparable(left), asComparable(right));
            case LESS_OR_EQUAL -> builder.lessThanOrEqualTo(asComparable(left), asComparable(right));
            default -> throw new AuthorizationException("Unsupported object comparison operator");
        };
    }

    @SuppressWarnings("SuspiciousNameCombination")
    private static Expression<? extends Number> arithmetic(
        AuthorizationCompiler.Binary binary,
        Root<?> root,
        CriteriaBuilder builder,
        Map<String, Class<?>> fields
    ) {
        Expression<? extends Number> left = valueNumber(binary.left(), binary.right(), root, builder, fields);
        Expression<? extends Number> right = valueNumber(binary.right(), binary.left(), root, builder, fields);
        return switch (binary.operator()) {
            case ADD -> builder.sum(left, right);
            case SUBTRACT -> builder.diff(left, right);
            case MULTIPLY -> builder.prod(left, right);
            case DIVIDE -> builder.quot(left, right);
            case MODULO -> throw new AuthorizationException("Modulo is not supported by object query dialects");
            default -> throw new AuthorizationException("Unsupported object arithmetic operator");
        };
    }

    private static Expression<? extends Number> valueNumber(
        AuthorizationCompiler.Expression expression,
        AuthorizationCompiler.Expression peer,
        Root<?> root,
        CriteriaBuilder builder,
        Map<String, Class<?>> fields
    ) {
        return value(expression, peer, root, builder, fields).as(Number.class);
    }

    private static Expression<?> value(
        AuthorizationCompiler.Expression expression,
        AuthorizationCompiler.Expression peer,
        Root<?> root,
        CriteriaBuilder builder,
        Map<String, Class<?>> fields
    ) {
        return switch (expression) {
            case AuthorizationCompiler.Reference reference -> root.get(objectField(reference, fields));
            case AuthorizationCompiler.LiteralValue literal -> builder.literal(coerce(literal.value(), peer, fields));
            case AuthorizationCompiler.Binary binary -> arithmetic(binary, root, builder, fields);
            case AuthorizationCompiler.Unary unary when (
                unary.operator() == AuthorizationCompiler.UnaryOperator.PLUS
            ) -> value(unary.operand(), peer, root, builder, fields);
            case AuthorizationCompiler.Unary unary when (
                unary.operator() == AuthorizationCompiler.UnaryOperator.MINUS
            ) -> builder.neg(value(unary.operand(), peer, root, builder, fields).as(Number.class));
            default -> throw new AuthorizationException("Unsupported object authorization value");
        };
    }

    private static String objectField(AuthorizationCompiler.Reference reference, Map<String, Class<?>> fields) {
        if (!reference.root().equals("object") || reference.path().size() != 1) {
            throw new AuthorizationException("Only direct object fields can be queried");
        }
        String field = reference.path().getFirst();
        if (!fields.containsKey(field)) throw new AuthorizationException("Object field is not queryable: " + field);
        return field;
    }

    private static Object coerce(Object value, AuthorizationCompiler.Expression peer, Map<String, Class<?>> fields) {
        if (!(peer instanceof AuthorizationCompiler.Reference reference)) return value;
        Class<?> type = fields.get(objectField(reference, fields));
        if (type == null || type.isInstance(value)) return value;
        if (type == UUID.class && value instanceof String text) return UUID.fromString(text);
        throw new AuthorizationException("Object authorization value has incompatible type");
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Expression<? extends Comparable> asComparable(Expression<?> expression) {
        return (Expression) expression;
    }

    public record ObjectAuthorizationPlan(
        AuthorizationCompiler.Expression predicate,
        List<StatementService.StatementInfo> matchedStatements,
        Map<String, Class<?>> fields
    ) {
        public ObjectAuthorizationPlan {
            matchedStatements = List.copyOf(matchedStatements);
            fields = Map.copyOf(fields);
        }
    }
}
