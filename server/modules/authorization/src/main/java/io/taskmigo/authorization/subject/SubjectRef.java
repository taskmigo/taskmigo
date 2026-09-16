package io.taskmigo.authorization.subject;

import java.util.Objects;
import java.util.UUID;

/// Identifies an authorization subject without importing the owning resource context.
public record SubjectRef(String type, UUID id) {
    /// Creates an opaque subject reference.
    public SubjectRef {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
        if (type.isBlank()) {
            throw new IllegalArgumentException("subject type must not be blank");
        }
    }
}
