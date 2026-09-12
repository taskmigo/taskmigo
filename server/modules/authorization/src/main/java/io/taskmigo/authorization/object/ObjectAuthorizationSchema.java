package io.taskmigo.authorization.object;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/// Defines the persistence-neutral, explicitly allow-listed object policy surface.
public interface ObjectAuthorizationSchema<Q> {
    /// Returns the API contract represented by this schema.
    Class<Q> objectType();

    /// Resolves one explicitly registered Object Authorization path.
    @SuppressWarnings("NullableProblems")
    Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath path);

    /// Returns every explicitly registered Object Authorization field.
    Collection<ObjectAuthorizationField> fields();

    /// Returns a stable identity for paths, types, nullability, and operators.
    default String identity() {
        return (
            this.objectType().getName() +
            ":" +
            this.fields()
                .stream()
                .map(ObjectAuthorizationSchema::canonicalField)
                .sorted()
                .collect(Collectors.joining("|", "[", "]"))
        );
    }

    private static String canonicalField(ObjectAuthorizationField field) {
        return (
            field.path().text() +
            ":" +
            field.type().getType().getTypeName() +
            ":" +
            field.nullable() +
            ":" +
            field
                .operators()
                .stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(",", "[", "]"))
        );
    }
}
