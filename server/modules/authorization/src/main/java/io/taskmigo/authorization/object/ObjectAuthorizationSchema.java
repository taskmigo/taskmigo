package io.taskmigo.authorization.object;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.language.Field;
import io.taskmigo.language.FieldId;
import io.taskmigo.language.FieldPath;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceType;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/// Defines the persistence-neutral, explicitly allow-listed object policy surface.
public interface ObjectAuthorizationSchema<Q> extends ObjectAuthorizationBinding<Q> {
    /// Returns the API contract represented by this schema.
    Class<Q> objectType();

    @Override
    default Optional<ObjectAuthorizationField> field(FieldId id) {
        return this.fields().stream()
            .filter(field -> field.id(this.resourceType().value()).equals(id))
            .findFirst();
    }

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

    private static LanguageType languageType(TypeDescriptor type) {
        Class<?> raw = type.rawType();
        if (raw == String.class || raw == Character.class || raw == char.class || raw == UUID.class) {
            return LanguageType.Scalar.STRING;
        }
        if (raw == Boolean.class || raw == boolean.class) {
            return LanguageType.Scalar.BOOL;
        }
        if (Number.class.isAssignableFrom(raw) || raw.isPrimitive()) {
            return LanguageType.Scalar.NUMBER;
        }
        return new LanguageType.StructuredType(raw.getName(), Map.of());
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
