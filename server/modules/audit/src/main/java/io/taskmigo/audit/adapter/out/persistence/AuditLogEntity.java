package io.taskmigo.audit.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
class AuditLogEntity {

    @Id
    UUID id;

    @Column(name = "entity_type", nullable = false, length = 64)
    String entityType;

    @Column(name = "entity_id", nullable = false)
    UUID entityId;

    @Column(name = "actor_id", nullable = false)
    UUID actorId;

    @Column(name = "actor_username", nullable = false, length = 100)
    String actorUsername;

    @Column(name = "occurred_at", nullable = false)
    Instant occurredAt;

    @Column(name = "changes_json", nullable = false, columnDefinition = "text")
    String changesJson;

    protected AuditLogEntity() {
        this.id = new UUID(0, 0);
        this.entityType = "";
        this.entityId = new UUID(0, 0);
        this.actorId = new UUID(0, 0);
        this.actorUsername = "";
        this.occurredAt = Instant.EPOCH;
        this.changesJson = "[]";
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
        this.id = Objects.requireNonNull(id);
        this.entityType = Objects.requireNonNull(entityType);
        this.entityId = Objects.requireNonNull(entityId);
        this.actorId = Objects.requireNonNull(actorId);
        this.actorUsername = Objects.requireNonNull(actorUsername);
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.changesJson = Objects.requireNonNull(changesJson);
    }
}
