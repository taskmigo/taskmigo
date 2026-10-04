package io.taskmigo.query;

import io.taskmigo.language.FieldId;
import java.util.Objects;
import java.util.Set;

/// Binds one semantic field identity to its execution path, value type, and supported query operators.
public record QueryFieldBinding(FieldId id, QueryPath executionPath, Class<?> valueType, Set<QueryOperator> operators) {
    public QueryFieldBinding {
        Objects.requireNonNull(id);
        Objects.requireNonNull(executionPath);
        Objects.requireNonNull(valueType);
        operators = Set.copyOf(operators);
    }
}
