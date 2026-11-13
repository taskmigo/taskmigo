package io.taskmigo.audit.model;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/// Identifies the responsible principal, not the application executing a mutation.
public record AuditActor(UUID id, String username, Type type) {
    public enum Type {
        USER,
        SYSTEM,
    }

    public AuditActor {
        Objects.requireNonNull(id);
        Objects.requireNonNull(username);
        Objects.requireNonNull(type);
    }

    public AuditActor(UUID id, String username) {
        this(id, username, Type.USER);
    }

    /// Creates a stable logical system actor with no User account or credentials.
    public static AuditActor system(String name) {
        if (name.isBlank() || name.length() > 100) {
            throw new IllegalArgumentException("System actor name must be 1 to 100 characters");
        }
        UUID id = UUID.nameUUIDFromBytes(("taskmigo:system-actor:" + name).getBytes(StandardCharsets.UTF_8));
        return new AuditActor(id, name, Type.SYSTEM);
    }
}
