package io.taskmigo.audit.event;

import io.taskmigo.audit.context.AuditContext;
import io.taskmigo.audit.context.AuditContexts;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.audit.model.AuditOperation;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Carries the immutable audit representation of a business mutation.
public record AuditEvent(
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
    public AuditEvent {
        if (entityId.isBlank() || entityId.length() > 255) {
            throw new IllegalArgumentException("Audit entity ID must be 1 to 255 characters");
        }
        occurredAt = occurredAt.truncatedTo(ChronoUnit.MILLIS);
        changes = List.copyOf(changes);
    }

    /// Preserves explicit user attribution used by runtime User mutations.
    public AuditEvent(
        UUID id,
        String entityType,
        UUID entityId,
        AuditActor actor,
        Instant occurredAt,
        List<AuditChange> changes
    ) {
        this(id, entityType, entityId.toString(), actor, occurredAt, changes, AuditOperation.UPDATE, null, null);
    }

    /// UUID-backed producers can reuse the same audit contract without serialization boilerplate.
    public AuditEvent(
        UUID id,
        String entityType,
        UUID entityId,
        AuditActor actor,
        Instant occurredAt,
        List<AuditChange> changes,
        AuditOperation operation,
        @Nullable String executor,
        @Nullable String causation
    ) {
        this(id, entityType, entityId.toString(), actor, occurredAt, changes, operation, executor, causation);
    }

    /// Uses a trusted entry-point context; absence of a context fails.
    public static AuditEvent fromContext(
        String entityType,
        String entityId,
        AuditOperation operation,
        Instant occurredAt,
        List<AuditChange> changes
    ) {
        AuditContext context = AuditContexts.require();
        return new AuditEvent(
            UUID.randomUUID(),
            entityType,
            entityId,
            context.actor(),
            occurredAt,
            changes,
            operation,
            context.executor().application(),
            context.causation().persistedValue()
        );
    }

    /// UUID-backed domain aggregates keep their native identifiers.
    public static AuditEvent fromContext(
        String entityType,
        UUID entityId,
        AuditOperation operation,
        Instant occurredAt,
        List<AuditChange> changes
    ) {
        return fromContext(entityType, entityId.toString(), operation, occurredAt, changes);
    }
}
