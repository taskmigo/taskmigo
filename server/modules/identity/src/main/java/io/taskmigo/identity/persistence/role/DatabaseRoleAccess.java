package io.taskmigo.identity.persistence.role;

import io.taskmigo.authorization.role.RoleAccess;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.identity.authorization.role.RoleService;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/// Adapts persisted authorization Roles to the transport-neutral Role access contract.
@Service
final class DatabaseRoleAccess implements RoleAccess {

    private final RoleService roles;

    DatabaseRoleAccess(RoleService roles) {
        this.roles = roles;
    }

    @Override
    public void requireRoles(Collection<UUID> roleIds) {
        this.roles.requireRoles(roleIds);
    }

    @Override
    public List<RoleInfo> effectiveRoles(Collection<UUID> roleIds) {
        return this.roles.effectiveRoles(roleIds);
    }
}
