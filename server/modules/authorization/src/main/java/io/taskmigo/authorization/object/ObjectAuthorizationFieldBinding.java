package io.taskmigo.authorization.object;

import io.taskmigo.language.FieldId;
import java.util.Objects;
import java.util.Set;

/// Maps one semantic Object Authorization field to execution metadata and supported operators.
public record ObjectAuthorizationFieldBinding(
    FieldId id,
    String executionPath,
    Class<?> valueType,
    Set<ObjectAuthorizationOperator> operators
) {
    public ObjectAuthorizationFieldBinding {
        Objects.requireNonNull(id);
        if (Objects.requireNonNull(executionPath).isBlank()) {
            throw new IllegalArgumentException("execution path must not be blank");
        }
        Objects.requireNonNull(valueType);
        operators = Set.copyOf(operators);
    }
}
