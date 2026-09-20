package io.taskmigo.authorization.role.infrastructure.persistence;

import io.taskmigo.authorization.role.application.RoleEffectiveStatementRepository;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/// Resolves effective Role Statement assignments with bounded closure and Role-row queries.
@Repository
public class JpaRoleEffectiveStatementRepository implements RoleEffectiveStatementRepository {

    private final RoleRepository roles;
    private final RoleHierarchyClosureRepository closures;

    public JpaRoleEffectiveStatementRepository(
        RoleRepository roles,
        RoleHierarchyClosureRepository closures
    ) {
        this.roles = roles;
        this.closures = closures;
    }

    @Override
    public Set<UUID> statementIdsForRoles(Collection<UUID> roleIds) {
        Set<UUID> requestedRoleIds = Set.copyOf(roleIds);
        if (requestedRoleIds.isEmpty()) {
            return Set.of();
        }

        Set<UUID> reachableRoleIds = new HashSet<>(requestedRoleIds);
        this.closures
            .findAllByIdAncestorRoleIdIn(requestedRoleIds)
            .stream()
            .map(RoleHierarchyClosureEntity::descendantRoleId)
            .forEach(reachableRoleIds::add);

        return this.roles
            .findDistinctByIdIn(reachableRoleIds)
            .stream()
            .flatMap(role -> role.statementIds().stream())
            .collect(Collectors.toUnmodifiableSet());
    }
}
