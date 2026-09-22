package io.taskmigo.authorization.subject.application;

import io.taskmigo.authorization.role.application.port.in.api.RoleService;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import io.taskmigo.authorization.subject.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.domain.SubjectGrants;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Coordinates direct Subject grant replacement and reference validation.
@Service
class DefaultSubjectGrantAssignmentService implements SubjectGrantAssignmentService {

    private final RoleService roles;
    private final StatementService statements;
    private final SubjectGrantRepository grants;

    DefaultSubjectGrantAssignmentService(
        RoleService roles,
        StatementService statements,
        SubjectGrantRepository grants
    ) {
        this.roles = roles;
        this.statements = statements;
        this.grants = grants;
    }

    @Override
    @Transactional
    public void setRoles(SubjectRef subject, Collection<UUID> roleIds) {
        Set<UUID> requested = Set.copyOf(roleIds);
        this.roles.requireRoles(requested);
        SubjectGrants current = this.grants.load(subject);
        this.grants.saveRoles(current.replacingRoles(requested));
    }

    @Override
    @Transactional
    public void setStatements(SubjectRef subject, Collection<UUID> statementIds) {
        Set<UUID> requested = Set.copyOf(statementIds);
        this.statements.requireStatements(requested);
        SubjectGrants current = this.grants.load(subject);
        this.grants.saveStatements(current.replacingStatements(requested));
    }
}
