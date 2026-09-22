package io.taskmigo.authorization.role.application.port.out;

import io.taskmigo.authorization.role.domain.hierarchy.RoleHierarchy;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// Loads and persists Role hierarchy state without exposing locking or closure-table strategy.
public interface RoleHierarchyRepository {
    RoleHierarchy loadForMutation();

    void replaceChildren(UUID roleId, Set<UUID> childIds, RoleHierarchy hierarchy);

    void synchronize(RoleHierarchy hierarchy);

    void remove(UUID roleId, RoleHierarchy hierarchy);

    List<UUID> descendantRoleIds(Collection<UUID> ancestorRoleIds);
}
