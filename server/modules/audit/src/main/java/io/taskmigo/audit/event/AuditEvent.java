package io.taskmigo.audit.event;

import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/// Carries a complete immutable mutation snapshot from the owning module to the audit worker.
///
/// @param id stable event identifier
/// @param entityType audited entity type
/// @param entityId audited entity identifier
/// @param actor authenticated actor captured before leaving the mutation transaction
/// @param occurredAt originating mutation time
/// @param changes field-level diff with sensitive values already removed
public record AuditEvent(
    UUID id,
    String entityType,
    UUID entityId,
    AuditActor actor,
    Instant occurredAt,
    List<AuditChange> changes
) {
    public AuditEvent {
        changes = List.copyOf(changes);
    }
}
