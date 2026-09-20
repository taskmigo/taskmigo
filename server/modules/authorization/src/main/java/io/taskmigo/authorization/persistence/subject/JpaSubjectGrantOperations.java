package io.taskmigo.authorization.persistence.subject;

import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.SubjectGrantRepository;
import io.taskmigo.authorization.subject.domain.SubjectGrants;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/// Implements direct Subject grant persistence with JPA bindings.
@Service
public class JpaSubjectGrantOperations implements SubjectGrantRepository {

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
    public SubjectGrants load(SubjectRef subject) {
        return new SubjectGrants(subject, this.roleIds(subject), this.statementIds(subject));
    }

    @Override
    public void saveRoles(SubjectGrants grants) {
        SubjectRef subject = grants.subject();
        this.roleBindings.deleteAllBySubjectTypeAndSubjectId(subject.type(), subject.id());
        this.roleBindings.saveAll(
            grants
                .roleIds()
                .stream()
                .map(roleId -> new SubjectRoleBindingEntity(UUID.randomUUID(), subject, roleId))
                .toList()
        );
    }

    @Override
    public void saveStatements(SubjectGrants grants) {
        SubjectRef subject = grants.subject();
        this.statementBindings.deleteAllBySubjectTypeAndSubjectId(subject.type(), subject.id());
        this.statementBindings.saveAll(
            grants
                .statementIds()
                .stream()
                .map(statementId -> new SubjectStatementBindingEntity(UUID.randomUUID(), subject, statementId))
                .toList()
        );
    }

    private Set<UUID> roleIds(SubjectRef subject) {
        return this.roleBindings
            .findAllBySubjectTypeAndSubjectId(subject.type(), subject.id())
            .stream()
            .map(SubjectRoleBindingEntity::roleId)
            .collect(Collectors.toUnmodifiableSet());
    }

    private Set<UUID> statementIds(SubjectRef subject) {
        return this.statementBindings
            .findAllBySubjectTypeAndSubjectId(subject.type(), subject.id())
            .stream()
            .map(SubjectStatementBindingEntity::statementId)
            .collect(Collectors.toUnmodifiableSet());
    }
}
