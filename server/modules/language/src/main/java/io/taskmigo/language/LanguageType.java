package io.taskmigo.language;

import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Represents a statically checked Embedded Language value type.
@SuppressWarnings("checkstyle:NeedBraces")
public sealed interface LanguageType permits LanguageType.Scalar, LanguageType.ListType, LanguageType.StructuredType {
    /// Primitive language types.
    enum Scalar implements LanguageType {
        BOOL,
        STRING,
        NUMBER,
        NULL,
    }

    /// Homogeneous list type.
    record ListType(LanguageType elementType) implements LanguageType {
        public ListType {
            Objects.requireNonNull(elementType);
        }
    }

    /// A schema-defined structured value with statically declared properties.
    record StructuredType(String name, Map<String, EnvironmentSchema.Field> fields) implements LanguageType {
        public StructuredType {
            Objects.requireNonNull(name);
            if (name.isBlank()) throw new IllegalArgumentException("structured type name must not be blank");
            fields = Map.copyOf(fields);
        }

        /// Returns the schema field for a statically declared property.
        public EnvironmentSchema.@Nullable Field field(String property) {
            return this.fields.get(property);
        }
    }

    /// Returns whether two types are compatible without coercion.
    static boolean compatible(LanguageType left, LanguageType right) {
        return left.equals(right);
    }

    /// Returns whether this type supports ordering.
    default boolean ordered() {
        return this == Scalar.STRING || this == Scalar.NUMBER;
    }
}
