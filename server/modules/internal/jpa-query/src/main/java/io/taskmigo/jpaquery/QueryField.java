package io.taskmigo.jpaquery;

import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.TypeDescriptor;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/// Binds one operation-scoped logical query field to a typed database-side JPA value expression.
public final class QueryField<E, V> {

    private static final Set<QueryOperator> APPROVED_OPERATORS = Set.of(
        QueryOperator.EQ,
        QueryOperator.NE,
        QueryOperator.GT,
        QueryOperator.GTE,
        QueryOperator.LT,
        QueryOperator.LTE,
        QueryOperator.CONTAINS
    );

    private final QueryPath path;
    private final TypeDescriptor type;
    private final boolean nullable;
    private final Class<V> javaType;
    private final JpaQueryValueExpression<E, V> expression;
    private final @Nullable JpaPath<E, V> staticPath;
    private final Set<QueryOperator> operators;
    private final boolean sortable;
    private final String sourceIdentity;

    private QueryField(
        QueryPath path,
        TypeDescriptor type,
        boolean nullable,
        Class<V> javaType,
        JpaQueryValueExpression<E, V> expression,
        @Nullable JpaPath<E, V> staticPath,
        Set<QueryOperator> operators,
        boolean sortable,
        String sourceIdentity
    ) {
        this.path = Objects.requireNonNull(path);
        this.type = Objects.requireNonNull(type);
        this.nullable = nullable;
        this.javaType = Objects.requireNonNull(javaType);
        this.expression = Objects.requireNonNull(expression);
        this.staticPath = staticPath;
        this.operators = Set.copyOf(operators);
        this.sortable = sortable;
        this.sourceIdentity = Objects.requireNonNull(sourceIdentity);
    }

    /// Creates a static JPA-path field without expression capabilities until explicitly allow-listed.
    public static <E, V> QueryField<E, V> of(JpaPath<E, V> path) {
        Objects.requireNonNull(path);
        Class<V> javaType = path.javaType();
        JpaQueryValueExpression<E, V> expression = new JpaQueryValueExpression<>() {
            @Override
            public Expression<V> resolve(Root<E> root, CriteriaQuery<?> query, CriteriaBuilder builder) {
                return path.resolve(root);
            }

            @Override
            public Class<V> javaType() {
                return javaType;
            }
        };
        return new QueryField<>(
            path.queryPath(),
            path.type(),
            path.nullable(),
            javaType,
            expression,
            path,
            Set.of(),
            false,
            path.queryPath().text()
        );
    }

    /// Creates a runtime field using its logical path as the default opaque source identity.
    public static <E, V> QueryField<E, V> dynamic(
        QueryPath path,
        TypeDescriptor type,
        boolean nullable,
        Class<V> javaType,
        JpaQueryValueExpression<E, V> expression
    ) {
        return dynamic(path, type, nullable, javaType, expression, path.text());
    }

    /// Creates a runtime field with an explicit persistence-neutral source identity for schema fingerprinting.
    public static <E, V> QueryField<E, V> dynamic(
        QueryPath path,
        TypeDescriptor type,
        boolean nullable,
        Class<V> javaType,
        JpaQueryValueExpression<E, V> expression,
        String sourceIdentity
    ) {
        if (!type.rawType().isAssignableFrom(javaType) && !javaType.isAssignableFrom(type.rawType())) {
            throw new IllegalArgumentException("Dynamic Query field Java type disagrees with its descriptor");
        }
        return new QueryField<>(
            path,
            type,
            nullable,
            javaType,
            expression,
            null,
            Set.of(),
            false,
            Objects.requireNonNull(sourceIdentity)
        );
    }

    /// Returns a field exposing exactly the supplied approved JPA comparison capabilities.
    public QueryField<E, V> operators(QueryOperator... operators) {
        Objects.requireNonNull(operators);
        Set<QueryOperator> requested = Set.copyOf(Arrays.asList(operators));
        Set<QueryOperator> unsupported = requested
            .stream()
            .filter(operator -> !APPROVED_OPERATORS.contains(operator))
            .collect(Collectors.toUnmodifiableSet());
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException("Unsupported JPA query field operators: " + unsupported);
        }
        return new QueryField<>(
            this.path,
            this.type,
            this.nullable,
            this.javaType,
            this.expression,
            this.staticPath,
            requested,
            this.sortable,
            this.sourceIdentity
        );
    }

    /// Marks this field as eligible for database-side ordering.
    public QueryField<E, V> sortable() {
        return new QueryField<>(
            this.path,
            this.type,
            this.nullable,
            this.javaType,
            this.expression,
            this.staticPath,
            this.operators,
            true,
            this.sourceIdentity
        );
    }

    /// Returns the persistence-neutral descriptor for this field.
    public QueryFieldDescriptor descriptor() {
        return new QueryFieldDescriptor(
            this.path,
            this.type,
            this.nullable,
            this.operators,
            this.sortable,
            this.sourceIdentity
        );
    }

    /// Returns the typed persistence value expression used by binders and sorters.
    public JpaQueryValueExpression<E, V> valueExpression() {
        return this.expression;
    }

    /// Returns the Java type used for persistence-value coercion.
    public Class<V> javaType() {
        return this.javaType;
    }

    /// Returns the typed static JPA path; runtime fields deliberately do not expose one.
    public JpaPath<E, V> jpaPath() {
        if (this.staticPath == null) {
            throw new IllegalStateException("Dynamic Query fields do not have a static JpaPath");
        }
        return this.staticPath;
    }
}
