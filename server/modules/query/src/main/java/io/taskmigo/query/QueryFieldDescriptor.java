package io.taskmigo.query;

import io.taskmigo.foundation.TypeDescriptor;
import java.util.Objects;
import java.util.Set;

/// Describes one logical field exposed by an operation-scoped query schema.
public record QueryFieldDescriptor(
    QueryPath path,
    TypeDescriptor type,
    boolean nullable,
    Set<QueryOperator> operators
) {
    public QueryFieldDescriptor {
        Objects.requireNonNull(path);
        Objects.requireNonNull(type);
        operators = Set.copyOf(operators);
    }

    /// Returns whether this field permits the supplied expression operator.
    public boolean supports(QueryOperator operator) {
        return this.operators.contains(operator);
    }
}
