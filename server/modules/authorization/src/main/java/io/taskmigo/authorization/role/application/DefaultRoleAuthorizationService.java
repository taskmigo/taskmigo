package io.taskmigo.authorization.role.application;

import io.taskmigo.authorization.role.application.api.RoleAuthorizationService;
import io.taskmigo.authorization.role.application.internal.RoleCommandService;
import io.taskmigo.authorization.statement.application.api.StatementService;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/// Orchestrates Role-to-Statement assignment through the canonical Role command path.
@Transactional
public class DefaultRoleAuthorizationService implements RoleAuthorizationService {

    private final RoleCommandService roles;
    private final StatementService statements;

    public DefaultRoleAuthorizationService(RoleCommandService roles, StatementService statements) {
        this.roles = roles;
        this.statements = statements;
    }

    @Override
    public void setStatements(UUID roleId, Collection<UUID> statementIds) {
        Set<UUID> requestedIds = Set.copyOf(statementIds);
        this.statements.requireStatements(requestedIds);
        this.roles.replaceStatements(roleId, requestedIds);
    }
}
