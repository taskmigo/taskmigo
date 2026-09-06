package io.taskmigo.embeddedlanguage;

import java.util.Objects;

/// Represents a statically checked Embedded Language value type.
public sealed interface LanguageType permits LanguageType.Scalar, LanguageType.ListType {
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

    /// Returns whether two types are compatible without coercion.
    static boolean compatible(LanguageType left, LanguageType right) {
        return left.equals(right);
    }

    /// Returns whether this type supports ordering.
    default boolean ordered() {
        return this == Scalar.STRING || this == Scalar.NUMBER;
    }
}
