package io.taskmigo.history;

import io.taskmigo.project.ProjectChanged;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "project_history")
@SuppressWarnings("NotNullFieldNotInitialized")
class ProjectHistoryEntity {

    @Id
    UUID id;

    @Column(name = "project_id", nullable = false)
    UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    ProjectChanged.Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 16)
    ProjectChanged.ActorType actorType;

    @Column(name = "actor_id", nullable = false)
    String actorId;

    @Column(name = "actor_display_name", nullable = false)
    String actorDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 16)
    ProjectChanged.@Nullable TargetType targetType;

    @Column(name = "target_id")
    @Nullable
    String targetId;

    @Column(name = "target_display_name")
    @Nullable
    String targetDisplayName;

    @Column(name = "changes_json", nullable = false, columnDefinition = "text")
    String changesJson;

    @Column(name = "data_json", nullable = false, columnDefinition = "text")
    String dataJson;

    @Column(name = "occurred_at", nullable = false)
    Instant occurredAt;

    protected ProjectHistoryEntity() {}

    ProjectHistoryEntity(ProjectChanged event, String changesJson, String dataJson) {
        this.id = event.id();
        this.projectId = event.projectId();
        this.action = event.action();
        this.actorType = event.actor().type();
        this.actorId = event.actor().id();
        this.actorDisplayName = event.actor().displayName();
        ProjectChanged.@Nullable Target target = event.target();
        if (target != null) {
            this.targetType = target.type();
            this.targetId = target.id();
            this.targetDisplayName = target.displayName();
        }
        this.changesJson = changesJson;
        this.dataJson = dataJson;
        this.occurredAt = event.occurredAt();
    }
}
