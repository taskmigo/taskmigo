package io.taskmigo.query;

import io.taskmigo.language.FieldId;
import java.util.Objects;
import java.util.Set;

/// Binds one semantic field identity to an execution path and supported query operators.
public record QueryFieldBinding(FieldId id, QueryPath executionPath, Set<QueryOperator> operators) {
    public QueryFieldBinding {
        Objects.requireNonNull(id);
        Objects.requireNonNull(executionPath);
        operators = Set.copyOf(operators);
    }
}
