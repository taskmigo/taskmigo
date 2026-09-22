package io.taskmigo.authorization.subject.application.service;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.role.application.port.in.api.RoleService;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.out.SubjectGrantRepository;
import io.taskmigo.authorization.subject.domain.SubjectGrants;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/// Coordinates direct Subject grant replacement and reference validation.
public final class DefaultSubjectGrantAssignmentService implements SubjectGrantAssignmentService {

    private final RoleService roles;
    private final StatementService statements;
    private final SubjectGrantRepository grants;
    private final TransactionRunner transactions;

    public DefaultSubjectGrantAssignmentService(
        RoleService roles,
        StatementService statements,
        SubjectGrantRepository grants,
        TransactionRunner transactions
    ) {
        this.roles = roles;
        this.statements = statements;
        this.grants = grants;
        this.transactions = transactions;
    }

    @Override
    public void setRoles(SubjectRef subject, Collection<UUID> roleIds) {
        this.transactions.write(() -> {
            Set<UUID> requested = Set.copyOf(roleIds);
            this.roles.requireRoles(requested);
            SubjectGrants current = this.grants.load(subject);
            this.grants.saveRoles(current.replacingRoles(requested));
        });
    }

    @Override
    public void setStatements(SubjectRef subject, Collection<UUID> statementIds) {
        this.transactions.write(() -> {
            Set<UUID> requested = Set.copyOf(statementIds);
            this.statements.requireStatements(requested);
            SubjectGrants current = this.grants.load(subject);
            this.grants.saveStatements(current.replacingStatements(requested));
        });
    }
}
