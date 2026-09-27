package io.taskmigo.audit.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/// Represents one immutable audit-log entry exposed to query adapters.
public record AuditLog(
    UUID id,
    UUID sourceEventId,
    String entityType,
    UUID entityId,
    AuditActor actor,
    Instant occurredAt,
    List<AuditFieldChange> changes
) {
    public AuditLog {
        changes = List.copyOf(changes);
    }

    /// Creates the deterministic audit entry for a mutation event.
    public static AuditLog from(AuditMutationEvent event) {
        return new AuditLog(
            event.eventId(),
            event.eventId(),
            event.entityType(),
            event.entityId(),
            event.actor(),
            event.occurredAt(),
            event.changes()
        );
    }
}
