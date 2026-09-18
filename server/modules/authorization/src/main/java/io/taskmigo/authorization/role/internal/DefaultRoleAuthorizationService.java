package io.taskmigo.authorization.role.internal;

import io.taskmigo.authorization.core.AuthorizationName;
import io.taskmigo.authorization.role.RoleAuthorizationService;
import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.role.internal.RoleStore.RoleState;
import io.taskmigo.authorization.statement.StatementService;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Orchestrates Role-to-Statement assignment use cases without depending on JPA.
@Service
class DefaultRoleAuthorizationService implements RoleAuthorizationService {

    private final RoleService roleService;
    private final RoleStore roles;
    private final StatementService statements;

    DefaultRoleAuthorizationService(RoleService roleService, RoleStore roles, StatementService statements) {
        this.roleService = roleService;
        this.roles = roles;
        this.statements = statements;
    }

    @Override
    @Transactional
    public UUID reconcile(@Nullable String name, @Nullable String description, Collection<UUID> statementIds) {
        Set<UUID> requestedIds = Set.copyOf(statementIds);
        this.statements.requireStatements(requestedIds);

        String validName = AuthorizationName.requiredRole(name, "name");
        RoleState existing = this.roles.findByName(validName).orElse(null);
        if (existing == null) {
            UUID id = this.roleService.createRole(validName, description, Set.of());
            this.roles.replaceStatements(id, requestedIds);
            return id;
        }

        this.roles.updateDescriptionAndStatements(existing.id(), description, requestedIds);
        return existing.id();
    }

    @Override
    @Transactional
    public void setStatements(UUID roleId, Collection<UUID> statementIds) {
        Set<UUID> requestedIds = Set.copyOf(statementIds);
        this.statements.requireStatements(requestedIds);
        if (this.roles.find(roleId).isEmpty()) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, "Role does not exist");
        }
        this.roles.replaceStatements(roleId, requestedIds);
    }
}
