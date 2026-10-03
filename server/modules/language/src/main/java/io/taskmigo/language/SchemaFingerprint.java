package io.taskmigo.language;

import java.util.Objects;

/// Identifies the complete effective semantic contract of a resource schema.
public record SchemaFingerprint(String value) implements Comparable<SchemaFingerprint> {
    public SchemaFingerprint {
        Objects.requireNonNull(value);
        if (value.isBlank()) {
            throw new IllegalArgumentException("schema fingerprint must not be blank");
        }
    }

    @Override
    public int compareTo(SchemaFingerprint other) {
        return this.value.compareTo(other.value);
    }
}
