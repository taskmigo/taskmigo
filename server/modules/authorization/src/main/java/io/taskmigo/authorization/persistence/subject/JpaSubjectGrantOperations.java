package io.taskmigo.authorization.persistence.subject;

import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.internal.SubjectGrantStore;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/// Implements subject-grant persistence with JPA entities and repositories.
@Service
public class JpaSubjectGrantOperations implements SubjectGrantStore {

    private final SubjectRoleBindingRepository roleBindings;
    private final SubjectStatementBindingRepository statementBindings;

    public JpaSubjectGrantOperations(
        SubjectRoleBindingRepository roleBindings,
        SubjectStatementBindingRepository statementBindings
    ) {
        this.roleBindings = roleBindings;
        this.statementBindings = statementBindings;
    }

    @Override
    public void replaceRoleIds(SubjectRef subject, Set<UUID> roleIds) {
        this.roleBindings.deleteAllBySubjectTypeAndSubjectId(subject.type(), subject.id());
        this.roleBindings.saveAll(
            roleIds
                .stream()
                .map(roleId -> new SubjectRoleBindingEntity(UUID.randomUUID(), subject, roleId))
                .toList()
        );
    }

    @Override
    public void replaceStatementIds(SubjectRef subject, Set<UUID> statementIds) {
        this.statementBindings.deleteAllBySubjectTypeAndSubjectId(subject.type(), subject.id());
        this.statementBindings.saveAll(
            statementIds
                .stream()
                .map(statementId -> new SubjectStatementBindingEntity(UUID.randomUUID(), subject, statementId))
                .toList()
        );
    }

    @Override
    public Set<UUID> roleIds(SubjectRef subject) {
        return this.roleBindings
            .findAllBySubjectTypeAndSubjectId(subject.type(), subject.id())
            .stream()
            .map(SubjectRoleBindingEntity::roleId)
            .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public Set<UUID> statementIds(SubjectRef subject) {
        return this.statementBindings
            .findAllBySubjectTypeAndSubjectId(subject.type(), subject.id())
            .stream()
            .map(SubjectStatementBindingEntity::statementId)
            .collect(Collectors.toUnmodifiableSet());
    }
}
