package io.taskmigo.foundation;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/// Describes a Java value type without coupling a contract to reflection or a framework type system.
public record TypeDescriptor(Class<?> rawType, List<TypeDescriptor> typeArguments) {
    public TypeDescriptor {
        Objects.requireNonNull(rawType);
        typeArguments = List.copyOf(typeArguments);
    }

    /// Creates a descriptor for a non-parameterized Java type.
    public static TypeDescriptor of(Class<?> rawType) {
        return new TypeDescriptor(rawType, List.of());
    }

    /// Creates a descriptor for a parameterized Java type.
    public static TypeDescriptor parameterized(Class<?> rawType, TypeDescriptor... typeArguments) {
        return new TypeDescriptor(rawType, List.of(typeArguments));
    }

    /// Returns a stable, framework-neutral representation suitable for contract identities.
    public String identity() {
        if (this.typeArguments.isEmpty()) {
            return this.rawType.getTypeName();
        }
        return (
            this.rawType.getTypeName() +
            "<" +
            this.typeArguments.stream().map(TypeDescriptor::identity).collect(Collectors.joining(",")) +
            ">"
        );
    }
}
