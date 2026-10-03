package io.taskmigo.language;

import java.util.Objects;

/// Identifies a semantic resource independently from its Java or persistence representation.
public record ResourceType(String value) implements Comparable<ResourceType> {
    public ResourceType {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("resource type must not be blank");
        }
    }

    /// Creates a resource type from its stable canonical value.
    public static ResourceType of(String value) {
        return new ResourceType(value);
    }

    @Override
    public int compareTo(ResourceType other) {
        return this.value.compareTo(other.value);
    }
}
