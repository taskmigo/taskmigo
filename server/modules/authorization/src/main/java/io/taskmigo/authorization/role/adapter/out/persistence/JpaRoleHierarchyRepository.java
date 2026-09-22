package io.taskmigo.authorization.role.adapter.out.persistence;

import io.taskmigo.authorization.role.application.port.out.RoleHierarchyRepository;
import io.taskmigo.authorization.role.domain.hierarchy.RoleHierarchy;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/// Adapts Role hierarchy persistence, graph locking, closure maintenance, and descendant reads to JPA.
@Repository
public class JpaRoleHierarchyRepository implements RoleHierarchyRepository {

    private final RoleRepository roles;
    private final RoleHierarchyClosureRepository closures;
    private final RoleHierarchyClosureWriter closureWriter;

    JpaRoleHierarchyRepository(
        RoleRepository roles,
        RoleHierarchyClosureRepository closures,
        RoleHierarchyClosureWriter closureWriter
    ) {
        this.roles = roles;
        this.closures = closures;
        this.closureWriter = closureWriter;
    }

    @Override
    public RoleHierarchy loadForMutation() {
        Map<UUID, Set<UUID>> childrenByParent = this.roles
            .findAllBy()
            .stream()
            .collect(
                Collectors.toMap(RoleEntity::id, role ->
                    role.childRoles().stream().map(RoleEntity::id).collect(Collectors.toSet())
                )
            );
        return RoleHierarchy.from(childrenByParent);
    }

    @Override
    public void replaceChildren(UUID roleId, Set<UUID> childIds, RoleHierarchy hierarchy) {
        RoleEntity role = this.roles.findById(roleId).orElseThrow();
        role.replaceChildRoles(this.roles.findAllByIdIn(childIds));
        this.roles.flush();
        this.synchronize(hierarchy);
    }

    @Override
    public void synchronize(RoleHierarchy hierarchy) {
        this.closureWriter.replace(
            hierarchy.roleIds(),
            roleId -> roleId,
            roleId -> hierarchy.reachableFrom(Set.of(roleId)),
            RoleHierarchyClosureEntity::new,
            RoleHierarchyClosureEntity.class
        );
    }

    @Override
    public void remove(UUID roleId, RoleHierarchy hierarchy) {
        this.roles.findAllBy().forEach(role -> {
            if (role.id().equals(roleId)) {
                if (!role.childRoles().isEmpty()) {
                    role.replaceChildRoles(Set.of());
                }
                return;
            }

            Set<RoleEntity> children = role.childRoles();
            if (children.stream().noneMatch(child -> child.id().equals(roleId))) {
                return;
            }
            role.replaceChildRoles(
                children
                    .stream()
                    .filter(child -> !child.id().equals(roleId))
                    .toList()
            );
        });
        this.roles.flush();
        this.synchronize(hierarchy);
    }

    @Override
    public List<UUID> descendantRoleIds(Collection<UUID> ancestorRoleIds) {
        return this.closures
            .findAllByIdAncestorRoleIdIn(ancestorRoleIds)
            .stream()
            .map(RoleHierarchyClosureEntity::descendantRoleId)
            .distinct()
            .sorted()
            .toList();
    }
}
