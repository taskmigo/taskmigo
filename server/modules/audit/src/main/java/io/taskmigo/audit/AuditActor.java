package io.taskmigo.audit;

import java.util.Objects;

/// Identifies the actor captured at the time an audited mutation occurs.
public record AuditActor(String id, String displayName) {
    public AuditActor {
        Objects.requireNonNull(id);
        Objects.requireNonNull(displayName);
    }
}
