package io.taskmigo.audit.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/// Carries the complete immutable mutation snapshot required by the background audit worker.
public record AuditMutationEvent(
    UUID eventId,
    String entityType,
    UUID entityId,
    AuditActor actor,
    Instant occurredAt,
    List<AuditFieldChange> changes
) {
    public AuditMutationEvent {
        Objects.requireNonNull(eventId);
        entityType = Objects.requireNonNull(entityType);
        Objects.requireNonNull(entityId);
        Objects.requireNonNull(actor);
        Objects.requireNonNull(occurredAt);
        changes = List.copyOf(changes);
        if (entityType.isBlank()) {
            throw new IllegalArgumentException("Audit entity type must not be blank");
        }
        if (changes.isEmpty()) {
            throw new IllegalArgumentException("Audit mutation must contain at least one change");
        }
    }

    /// Creates an audit event for a User mutation.
    public static AuditMutationEvent user(
        UUID eventId,
        UUID userId,
        AuditActor actor,
        Instant occurredAt,
        List<AuditFieldChange> changes
    ) {
        return new AuditMutationEvent(eventId, AuditEntityType.USER.code(), userId, actor, occurredAt, changes);
    }
}
