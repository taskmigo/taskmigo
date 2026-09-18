package io.taskmigo.authorization.role.internal;

import io.taskmigo.authorization.role.RoleAuthorizationService;
import io.taskmigo.authorization.role.RoleException;
import io.taskmigo.authorization.statement.StatementService;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Orchestrates Role-to-Statement assignment use cases without depending on JPA.
@Service
class DefaultRoleAuthorizationService implements RoleAuthorizationService {

    private final RoleStore roles;
    private final StatementService statements;

    DefaultRoleAuthorizationService(RoleStore roles, StatementService statements) {
        this.roles = roles;
        this.statements = statements;
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
