package io.taskmigo.auth.authorization.object;

import java.util.Set;
import org.springframework.core.ResolvableType;

/// Describes one explicitly queryable Object Authorization path.
public record ObjectAuthorizationField(
    ObjectAuthorizationPath path,
    ResolvableType type,
    boolean nullable,
    Set<ObjectAuthorizationOperator> operators
) {
    public ObjectAuthorizationField {
        operators = Set.copyOf(operators);
    }

    /// Creates a field with the standard scalar comparison operators.
    public ObjectAuthorizationField(ObjectAuthorizationPath path, ResolvableType type, boolean nullable) {
        this(path, type, nullable, Set.of(
            ObjectAuthorizationOperator.EQ,
            ObjectAuthorizationOperator.NE,
            ObjectAuthorizationOperator.GT,
            ObjectAuthorizationOperator.GE,
            ObjectAuthorizationOperator.LT,
            ObjectAuthorizationOperator.LE,
            ObjectAuthorizationOperator.IN
        ));
    }
}
