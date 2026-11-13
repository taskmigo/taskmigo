package io.taskmigo.audit.persistence;

import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditOperation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "audit_logs")
class AuditLogEntity {

    @Id
    UUID id;

    @Column(name = "entity_type", nullable = false, length = 64)
    String entityType;

    @Column(name = "entity_id", nullable = false)
    String entityId;

    @Column(name = "actor_id", nullable = false)
    UUID actorId;

    @Column(name = "actor_username", nullable = false, length = 100)
    String actorUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    AuditActor.Type actorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 16)
    AuditOperation operation;

    @Nullable
    @Column(name = "executor", length = 100)
    String executor;

    @Nullable
    @Column(name = "causation")
    String causation;

    @Column(name = "occurred_at", nullable = false)
    Instant occurredAt;

    @Column(name = "changes_json", nullable = false, columnDefinition = "text")
    String changesJson;

    protected AuditLogEntity() {
        this.id = new UUID(0, 0);
        this.entityType = "";
        this.entityId = "";
        this.actorId = new UUID(0, 0);
        this.actorUsername = "";
        this.actorType = AuditActor.Type.USER;
        this.operation = AuditOperation.UPDATE;
        this.occurredAt = Instant.EPOCH;
        this.changesJson = "[]";
    }

    AuditLogEntity(
        UUID id,
        String entityType,
        String entityId,
        UUID actorId,
        String actorUsername,
        Instant occurredAt,
        String changesJson
    ) {
        this.id = Objects.requireNonNull(id);
        this.entityType = Objects.requireNonNull(entityType);
        this.entityId = Objects.requireNonNull(entityId);
        this.actorId = Objects.requireNonNull(actorId);
        this.actorUsername = Objects.requireNonNull(actorUsername);
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.changesJson = Objects.requireNonNull(changesJson);
        this.actorType = AuditActor.Type.USER;
        this.operation = AuditOperation.UPDATE;
    }

    AuditLogEntity(
        UUID id,
        String entityType,
        String entityId,
        AuditActor actor,
        Instant occurredAt,
        String changesJson,
        AuditOperation operation,
        @Nullable String executor,
        @Nullable String causation
    ) {
        this(id, entityType, entityId, actor.id(), actor.username(), occurredAt, changesJson);
        this.actorType = actor.type();
        this.operation = Objects.requireNonNull(operation);
        this.executor = executor;
        this.causation = causation;
    }

    AuditLogEntity(
        UUID id,
        String entityType,
        UUID entityId,
        UUID actorId,
        String actorUsername,
        Instant occurredAt,
        String changesJson
    ) {
        this(id, entityType, entityId.toString(), actorId, actorUsername, occurredAt, changesJson);
    }
}
