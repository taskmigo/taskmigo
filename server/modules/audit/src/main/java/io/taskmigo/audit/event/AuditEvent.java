package io.taskmigo.audit.event;

import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/// Carries the complete immutable audit representation of one business mutation.
///
/// @param id stable mutation/audit identifier
/// @param entityType audited entity type
/// @param entityId audited entity identifier
/// @param actor actor captured by the originating operation
/// @param occurredAt originating mutation time normalized to millisecond precision
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
        occurredAt = occurredAt.truncatedTo(ChronoUnit.MILLIS);
        changes = List.copyOf(changes);
    }
}
