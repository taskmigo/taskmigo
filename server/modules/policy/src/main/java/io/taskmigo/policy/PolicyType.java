package io.taskmigo.policy;

import java.util.Objects;

/// Represents a statically checked Policy Language value type.
public sealed interface PolicyType permits PolicyType.Scalar, PolicyType.ListType {
    /// Primitive language types.
    enum Scalar implements PolicyType {
        BOOL,
        STRING,
        NUMBER,
        NULL,
    }

    /// Homogeneous list type.
    record ListType(PolicyType elementType) implements PolicyType {
        public ListType {
            Objects.requireNonNull(elementType);
        }
    }

    /// Returns whether two types are compatible without coercion.
    static boolean compatible(PolicyType left, PolicyType right) {
        return left.equals(right);
    }

    /// Returns whether this type supports ordering.
    default boolean ordered() {
        return this == Scalar.STRING || this == Scalar.NUMBER;
    }
}
