package io.taskmigo.query;

import java.util.Objects;
import java.util.Set;
import org.springframework.core.ResolvableType;

/// Describes one explicitly queryable API field.
public record QueryField(QueryPath path, ResolvableType type, boolean nullable, Set<QueryOperator> operators) {
    public QueryField {
        Objects.requireNonNull(path);
        Objects.requireNonNull(type);
        operators = Set.copyOf(operators);
    }

    /// Creates a field with the standard scalar operators.
    public QueryField(QueryPath path, ResolvableType type, boolean nullable) {
        this(path, type, nullable, Set.of(
            QueryOperator.EQ, QueryOperator.NE, QueryOperator.GT, QueryOperator.GE,
            QueryOperator.LT, QueryOperator.LE, QueryOperator.IN
        ));
    }
}
