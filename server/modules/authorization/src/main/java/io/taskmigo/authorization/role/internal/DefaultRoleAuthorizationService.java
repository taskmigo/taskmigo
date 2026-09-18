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

/// Implements Role-to-Statement assignment use cases independently from persistence.
@Service
final class DefaultRoleAuthorizationService implements RoleAuthorizationService {

    private final RoleService roles;
    private final StatementService statements;
    private final RoleStore store;

    DefaultRoleAuthorizationService(RoleService roles, StatementService statements, RoleStore store) {
        this.roles = roles;
        this.statements = statements;
        this.store = store;
    }

    @Override
    @Transactional
    public UUID reconcile(@Nullable String name, @Nullable String description, Collection<UUID> statementIds) {
        Set<UUID> requestedStatementIds = Set.copyOf(statementIds);
        this.statements.requireStatements(requestedStatementIds);

        String validName = AuthorizationName.requiredRole(name, "name");
        RoleState role = this.store.findByName(validName).orElse(null);
        if (role == null) {
            UUID id = this.roles.createRole(validName, description, Set.of());
            this.store.replaceStatements(id, requestedStatementIds);
            return id;
        }
        this.store.updateDescriptionAndStatements(role.id(), description, requestedStatementIds);
        return role.id();
    }

    @Override
    @Transactional
    public void setStatements(UUID roleId, Collection<UUID> statementIds) {
        Set<UUID> requestedStatementIds = Set.copyOf(statementIds);
        this.statements.requireStatements(requestedStatementIds);
        if (this.store.find(roleId).isEmpty()) {
            throw new RoleException(RoleException.Type.BAD_REQUEST, "Role does not exist");
        }
        this.store.replaceStatements(roleId, requestedStatementIds);
    }
}
