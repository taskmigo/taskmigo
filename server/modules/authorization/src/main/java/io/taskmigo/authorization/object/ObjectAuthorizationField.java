package io.taskmigo.authorization.object;

import io.taskmigo.foundation.TypeDescriptor;
import java.util.Set;

/// Describes one explicitly queryable Object Authorization path.
public record ObjectAuthorizationField(
    ObjectAuthorizationPath path,
    TypeDescriptor type,
    boolean nullable,
    Set<ObjectAuthorizationOperator> operators
) {
    public ObjectAuthorizationField {
        operators = Set.copyOf(operators);
    }

    /// Creates a field with the standard scalar comparison operators.
    public ObjectAuthorizationField(ObjectAuthorizationPath path, TypeDescriptor type, boolean nullable) {
        this(
            path,
            type,
            nullable,
            Set.of(
                ObjectAuthorizationOperator.EQ,
                ObjectAuthorizationOperator.NE,
                ObjectAuthorizationOperator.GT,
                ObjectAuthorizationOperator.GE,
                ObjectAuthorizationOperator.LT,
                ObjectAuthorizationOperator.LE,
                ObjectAuthorizationOperator.IN
            )
        );
    }
}
