package io.taskmigo.query;

import io.taskmigo.foundation.TypeDescriptor;
import java.util.Objects;
import java.util.Set;

/// Describes one explicitly queryable API field.
public record QueryField(QueryPath path, TypeDescriptor type, boolean nullable, Set<QueryOperator> operators) {
    public QueryField {
        Objects.requireNonNull(path);
        Objects.requireNonNull(type);
        operators = Set.copyOf(operators);
    }

    /// Creates a field with the standard scalar comparison operators.
    public QueryField(QueryPath path, TypeDescriptor type, boolean nullable) {
        this(
            path,
            type,
            nullable,
            Set.of(
                QueryOperator.EQ,
                QueryOperator.NE,
                QueryOperator.GT,
                QueryOperator.GTE,
                QueryOperator.LT,
                QueryOperator.LTE
            )
        );
    }
}
