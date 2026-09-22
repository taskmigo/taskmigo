package io.taskmigo.authorization.role.application.service;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.role.application.port.in.api.RoleAuthorizationService;
import io.taskmigo.authorization.role.application.port.in.internal.RoleCommandService;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/// Orchestrates Role-to-Statement assignment through the canonical Role command path.
public final class DefaultRoleAuthorizationService implements RoleAuthorizationService {

    private final RoleCommandService roles;
    private final StatementService statements;
    private final TransactionRunner transactions;

    public DefaultRoleAuthorizationService(
        RoleCommandService roles,
        StatementService statements,
        TransactionRunner transactions
    ) {
        this.roles = roles;
        this.statements = statements;
        this.transactions = transactions;
    }

    @Override
    public void setStatements(UUID roleId, Collection<UUID> statementIds) {
        this.transactions.write(() -> {
            Set<UUID> requestedIds = Set.copyOf(statementIds);
            this.statements.requireStatements(requestedIds);
            this.roles.replaceStatements(roleId, requestedIds);
        });
    }
}
