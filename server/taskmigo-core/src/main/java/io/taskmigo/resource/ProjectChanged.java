package io.taskmigo.resource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Describes one project-domain audit event retained by the history module.
///
/// Actor and target display names are snapshots so old history remains readable after later renames or deletion.
public record ProjectChanged(
    UUID id,
    UUID projectId,
    Action action,
    Actor actor,
    @Nullable Target target,
    List<Change> changes,
    Map<String, Object> data,
    Instant occurredAt
) {
    public ProjectChanged {
        changes = List.copyOf(changes);
        data = Map.copyOf(data);
    }

    public enum Action {
        PROJECT_CREATED,
        PROJECT_UPDATED,
        PROJECT_ARCHIVED,
        MEMBER_JOINED,
        MEMBER_ADDED,
        MEMBER_LEFT,
        MEMBER_REMOVED,
        MEMBER_ROLES_CHANGED,
    }

    public enum ActorType {
        USER,
        SERVICE,
        SYSTEM,
    }

    public enum TargetType {
        PROJECT,
        USER,
        GROUP,
    }

    public record Actor(ActorType type, String id, String displayName) {}

    public record Target(TargetType type, String id, String displayName) {}

    public record Change(String field, @Nullable Object before, @Nullable Object after) {}
}
