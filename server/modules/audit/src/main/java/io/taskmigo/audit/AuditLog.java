package io.taskmigo.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/// One immutable audit record returned by the audit query API.
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
