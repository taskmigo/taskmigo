package io.taskmigo.language;

import java.util.Objects;

/// Identifies a semantic field independently from its source path or persistence representation.
public record FieldId(String value) implements Comparable<FieldId> {
    public FieldId {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("field id must not be blank");
        }
    }

    /// Creates a field identity from its stable canonical value.
    public static FieldId of(String value) {
        return new FieldId(value);
    }

    @Override
    public int compareTo(FieldId other) {
        return this.value.compareTo(other.value);
    }
}
