package io.taskmigo.audit;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/// Immutable mutation snapshot externalized transactionally to the durable audit outbox.
public record AuditEvent(
    UUID eventId,
    String entityType,
    UUID entityId,
    AuditActor actor,
    Instant occurredAt,
    List<AuditChange> changes
) {
    public AuditEvent {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(entityType);
        Objects.requireNonNull(entityId);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(occurredAt);
        changes = List.copyOf(changes);
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("Audit event must contain at least one change");
        }
    }
}
