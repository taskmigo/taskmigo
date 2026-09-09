package io.taskmigo.auth.authorization.object;

import java.util.Collection;
import java.util.Optional;

/// Defines the persistence-neutral, explicitly allow-listed object policy surface.
public interface ObjectAuthorizationSchema<Q> {
    /// Returns the API contract represented by this schema.
    Class<Q> objectType();

    /// Resolves one explicitly registered Object Authorization path.
    Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath path);

    /// Returns every explicitly registered Object Authorization field.
    Collection<ObjectAuthorizationField> fields();

    /// Returns a stable identity for paths, types, nullability, and operators.
    default String identity() {
        return this.objectType().getName() + ":" + this.fields().stream().map(Object::toString).sorted().toList();
    }
}
