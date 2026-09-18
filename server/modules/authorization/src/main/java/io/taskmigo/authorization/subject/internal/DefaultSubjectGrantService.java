package io.taskmigo.authorization.subject.internal;

import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.statement.StatementService;
import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.authorization.subject.SubjectRef;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Orchestrates subject grants through persistence-neutral ports.
@Service
class DefaultSubjectGrantService implements SubjectGrantService {

    private final RoleService roles;
    private final StatementService statements;
    private final SubjectGrantStore grants;

    DefaultSubjectGrantService(RoleService roles, StatementService statements, SubjectGrantStore grants) {
        this.roles = roles;
        this.statements = statements;
        this.grants = grants;
    }

    @Override
    @Transactional
    public void setRoles(SubjectRef subject, Collection<UUID> roleIds) {
        Set<UUID> requested = Set.copyOf(roleIds);
        this.roles.requireRoles(requested);
        this.grants.replaceRoleIds(subject, requested);
    }

    @Override
    @Transactional
    public void setStatements(SubjectRef subject, Collection<UUID> statementIds) {
        Set<UUID> requested = Set.copyOf(statementIds);
        this.statements.requireStatements(requested);
        this.grants.replaceStatementIds(subject, requested);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> roleIds(SubjectRef subject) {
        return this.grants.roleIds(subject);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> statementIds(SubjectRef subject) {
        return this.grants.statementIds(subject);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRoles(Collection<SubjectRef> subjects) {
        Set<UUID> roleIds = new LinkedHashSet<>();
        for (SubjectRef subject : subjects) {
            roleIds.addAll(this.grants.roleIds(subject));
        }
        return this.roles.effectiveRoles(roleIds);
    }
}
