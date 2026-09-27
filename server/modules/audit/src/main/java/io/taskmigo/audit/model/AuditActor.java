package io.taskmigo.audit.model;

import java.util.Objects;
import java.util.UUID;

/// Identifies the authenticated actor captured at mutation time.
public record AuditActor(UUID id, String username) {
    public AuditActor {
        Objects.requireNonNull(id);
        username = Objects.requireNonNull(username);
    }
}
