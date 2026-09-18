package io.taskmigo.authorization.persistence.subject;

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
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Owns direct Role and Statement bindings for opaque authorization subjects.
@Service
public class JpaSubjectGrantOperations implements SubjectGrantService {

    private final RoleService roles;
    private final StatementService statements;
    private final SubjectRoleBindingRepository roleBindings;
    private final SubjectStatementBindingRepository statementBindings;

    public JpaSubjectGrantOperations(
        RoleService roles,
        StatementService statements,
        SubjectRoleBindingRepository roleBindings,
        SubjectStatementBindingRepository statementBindings
    ) {
        this.roles = roles;
        this.statements = statements;
        this.roleBindings = roleBindings;
        this.statementBindings = statementBindings;
    }

    /// Replaces the Roles directly bound to one subject.
    @Transactional
    public void setRoles(SubjectRef subject, Collection<UUID> roleIds) {
        Set<UUID> requested = Set.copyOf(roleIds);
        this.roles.requireRoles(requested);
        this.roleBindings.deleteAllBySubjectTypeAndSubjectId(subject.type(), subject.id());
        this.roleBindings.saveAll(
            requested
                .stream()
                .map(roleId -> new SubjectRoleBindingEntity(UUID.randomUUID(), subject, roleId))
                .toList()
        );
    }

    /// Replaces the Statements directly bound to one subject.
    @Transactional
    public void setStatements(SubjectRef subject, Collection<UUID> statementIds) {
        Set<UUID> requested = Set.copyOf(statementIds);
        this.statements.requireStatements(requested);
        this.statementBindings.deleteAllBySubjectTypeAndSubjectId(subject.type(), subject.id());
        this.statementBindings.saveAll(
            requested
                .stream()
                .map(statementId -> new SubjectStatementBindingEntity(UUID.randomUUID(), subject, statementId))
                .toList()
        );
    }

    /// Returns the Roles directly bound to one subject.
    @Transactional(readOnly = true)
    public Set<UUID> roleIds(SubjectRef subject) {
        return this.roleBindings
            .findAllBySubjectTypeAndSubjectId(subject.type(), subject.id())
            .stream()
            .map(SubjectRoleBindingEntity::roleId)
            .collect(Collectors.toUnmodifiableSet());
    }

    /// Returns the Statements directly bound to one subject.
    @Transactional(readOnly = true)
    public Set<UUID> statementIds(SubjectRef subject) {
        return this.statementBindings
            .findAllBySubjectTypeAndSubjectId(subject.type(), subject.id())
            .stream()
            .map(SubjectStatementBindingEntity::statementId)
            .collect(Collectors.toUnmodifiableSet());
    }

    /// Resolves all direct and inherited Roles bound to the supplied effective subjects.
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRoles(Collection<SubjectRef> subjects) {
        Set<UUID> roleIds = new LinkedHashSet<>();
        for (SubjectRef subject : subjects) {
            roleIds.addAll(this.roleIds(subject));
        }
        return this.roles.effectiveRoles(roleIds);
    }
}
