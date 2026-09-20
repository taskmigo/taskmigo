package io.taskmigo.identity.group.application;

import io.taskmigo.identity.group.domain.hierarchy.GroupHierarchy;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// Loads and persists Group hierarchy domain state without exposing locking or derived-storage strategy.
public interface GroupHierarchyRepository {
    GroupHierarchy loadForMutation();

    void replaceChildren(UUID groupId, Set<UUID> childIds, GroupHierarchy hierarchy);

    void synchronize(GroupHierarchy hierarchy);

    void remove(UUID groupId, GroupHierarchy hierarchy);

    List<UUID> descendantGroupIds(Collection<UUID> ancestorGroupIds);
}
