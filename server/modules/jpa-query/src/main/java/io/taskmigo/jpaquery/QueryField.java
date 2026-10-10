package io.taskmigo.jpaquery;

import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperator;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// Binds one operation-scoped logical query field to a typed JPA path.
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

    private final JpaPath<E, V> path;
    private final Set<QueryOperator> operators;

    private QueryField(JpaPath<E, V> path, Set<QueryOperator> operators) {
        this.path = Objects.requireNonNull(path);
        this.operators = Set.copyOf(operators);
    }

    /// Creates a field without expression capabilities until the schema explicitly allow-lists them.
    public static <E, V> QueryField<E, V> of(JpaPath<E, V> path) {
        return new QueryField<>(path, Set.of());
    }

    /// Returns a field exposing exactly the supplied Enhancement #237 JPA comparison capabilities.
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
        return new QueryField<>(this.path, requested);
    }

    /// Returns the persistence-neutral descriptor derived from the JPA metadata path.
    public QueryFieldDescriptor descriptor() {
        return new QueryFieldDescriptor(this.path.queryPath(), this.path.type(), this.path.nullable(), this.operators);
    }

    /// Returns the typed JPA path used by persistence binders.
    public JpaPath<E, V> jpaPath() {
        return this.path;
    }
}
