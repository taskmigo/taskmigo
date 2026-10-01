package io.taskmigo.audit.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/// Represents one immutable audit record.
///
/// @param id stable audit-log identifier
/// @param entityType audited entity type
/// @param entityId audited entity identifier
/// @param actor authenticated actor captured at mutation time
/// @param occurredAt time of the originating mutation
/// @param changes field-level diff
public record AuditLog(
    UUID id,
    String entityType,
    UUID entityId,
    AuditActor actor,
    Instant occurredAt,
    List<AuditChange> changes
) {
    public AuditLog {
        changes = List.copyOf(changes);
    }
}
