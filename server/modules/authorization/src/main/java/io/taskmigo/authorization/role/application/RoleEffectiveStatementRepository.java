package io.taskmigo.authorization.role.application;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/// Resolves Statement ids from directly assigned Roles and their reachable child Roles using bounded reads.
public interface RoleEffectiveStatementRepository {
    Set<UUID> statementIdsForRoles(Collection<UUID> roleIds);
}
