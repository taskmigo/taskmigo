package io.taskmigo.audit.adapter.out.persistence;

import io.taskmigo.audit.AuditActor;
import io.taskmigo.audit.AuditEvent;
import io.taskmigo.audit.AuditLog;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
class AuditLogEntity {

    @Id
    private UUID id;

    @Column(name = "source_event_id", nullable = false, unique = true)
    private UUID sourceEventId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "actor_display_name", nullable = false)
    private String actorDisplayName;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @OneToMany(mappedBy = "log", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    private List<AuditChangeEntity> changes = new ArrayList<>();

    protected AuditLogEntity() {}

    private AuditLogEntity(AuditEvent event) {
        this.id = UUID.randomUUID();
        this.sourceEventId = event.eventId();
        this.entityType = event.entityType();
        this.entityId = event.entityId();
        this.actorId = event.actor().id();
        this.actorDisplayName = event.actor().displayName();
        this.occurredAt = event.occurredAt();
        for (int index = 0; index < event.changes().size(); index++) {
            this.changes.add(new AuditChangeEntity(this, index, event.changes().get(index)));
        }
    }

    static AuditLogEntity from(AuditEvent event) {
        return new AuditLogEntity(event);
    }

    AuditLog toModel() {
        return new AuditLog(
            this.id,
            this.entityType,
            this.entityId,
            new AuditActor(this.actorId, this.actorDisplayName),
            this.occurredAt,
            this.changes.stream().map(AuditChangeEntity::toModel).toList()
        );
    }
}
