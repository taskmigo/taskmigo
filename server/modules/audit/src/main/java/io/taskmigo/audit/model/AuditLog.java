package io.taskmigo.audit.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Represents a persisted immutable entity audit entry.
public record AuditLog(
    UUID id,
    String entityType,
    String entityId,
    AuditActor actor,
    Instant occurredAt,
    List<AuditChange> changes,
    AuditOperation operation,
    @Nullable String executor,
    @Nullable String causation
) {
    public AuditLog {
        changes = List.copyOf(changes);
    }

    public AuditLog(
        UUID id,
        String entityType,
        UUID entityId,
        AuditActor actor,
        Instant occurredAt,
        List<AuditChange> changes
    ) {
        this(id, entityType, entityId.toString(), actor, occurredAt, changes, AuditOperation.UPDATE, null, null);
    }
}
