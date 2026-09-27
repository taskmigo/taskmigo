package io.taskmigo.audit.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@SuppressWarnings("NotNullFieldNotInitialized")
class AuditLogEntity {

    @Id
    UUID id;

    @Column(name = "source_event_id", nullable = false, unique = true)
    UUID sourceEventId;

    @Column(name = "entity_type", nullable = false, length = 100)
    String entityType;

    @Column(name = "entity_id", nullable = false)
    UUID entityId;

    @Column(name = "actor_id", nullable = false)
    UUID actorId;

    @Column(name = "actor_username", nullable = false, length = 255)
    String actorUsername;

    @Column(name = "occurred_at", nullable = false)
    Instant occurredAt;

    @Column(name = "changes_json", nullable = false, columnDefinition = "text")
    String changesJson;

    protected AuditLogEntity() {}

    AuditLogEntity(
        UUID id,
        UUID sourceEventId,
        String entityType,
        UUID entityId,
        UUID actorId,
        String actorUsername,
        Instant occurredAt,
        String changesJson
    ) {
        this.id = id;
        this.sourceEventId = sourceEventId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.actorId = actorId;
        this.actorUsername = actorUsername;
        this.occurredAt = occurredAt;
        this.changesJson = changesJson;
    }
}
