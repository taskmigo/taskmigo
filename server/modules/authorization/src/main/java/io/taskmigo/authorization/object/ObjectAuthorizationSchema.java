package io.taskmigo.authorization.object;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;
import io.taskmigo.language.Field;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceType;

/// Defines the persistence-neutral, explicitly allow-listed object policy surface.
public interface ObjectAuthorizationSchema<Q> {
    /// Returns the API contract represented by this schema.
    Class<Q> objectType();

    /// Resolves one explicitly registered Object Authorization path.
    @SuppressWarnings("NullableProblems")
    Optional<ObjectAuthorizationField> field(ObjectAuthorizationPath path);

    /// Returns every explicitly registered Object Authorization field.
    Collection<ObjectAuthorizationField> fields();

    /// Returns the semantic resource identity shared by authorization and query compilation.
    default ResourceType resourceType() {
        return ResourceType.of("taskmigo:authorization:object:" + this.objectType().getName());
    }

    /// Projects this legacy declaration into the framework-neutral semantic schema.
    default ResourceSchema resourceSchema() {
        ResourceType type = this.resourceType();
        return ResourceSchema.of(type, this.fields().stream().map(field -> new Field(
            field.id(type.value()),
            FieldPath.parse(field.path().text()),
            languageType(field.type()),
            field.nullable()
        )).toList());
    }

    private static LanguageType languageType(io.taskmigo.foundation.TypeDescriptor type) {
        Class<?> raw = type.rawType();
        if (raw == String.class || raw == Character.class || raw == char.class || raw == java.util.UUID.class) {
            return LanguageType.Scalar.STRING;
        }
        if (raw == Boolean.class || raw == boolean.class) {
            return LanguageType.Scalar.BOOL;
        }
        if (Number.class.isAssignableFrom(raw) || raw.isPrimitive()) {
            return LanguageType.Scalar.NUMBER;
        }
        return new LanguageType.StructuredType(raw.getName(), java.util.Map.of());
    }

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
            field.type().identity() +
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
