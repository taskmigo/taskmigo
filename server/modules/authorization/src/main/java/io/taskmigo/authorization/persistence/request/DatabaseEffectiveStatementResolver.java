package io.taskmigo.authorization.persistence.request;

import io.taskmigo.authorization.persistence.role.RoleEntity;
import io.taskmigo.authorization.persistence.role.RoleRepository;
import io.taskmigo.authorization.persistence.statement.StatementEntity;
import io.taskmigo.authorization.persistence.statement.StatementRepository;
import io.taskmigo.authorization.spi.EffectiveStatement;
import io.taskmigo.authorization.spi.EffectiveStatementResolver;
import io.taskmigo.authorization.spi.EffectiveSubjectResolver;
import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.authorization.subject.SubjectRef;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// Resolves effective Statements entirely from Access Control state after resolving opaque effective subjects.
@Service
public class DatabaseEffectiveStatementResolver implements EffectiveStatementResolver {

    private final EffectiveSubjectResolver subjects;
    private final SubjectGrantService grants;
    private final RoleRepository roles;
    private final StatementRepository statements;

    DatabaseEffectiveStatementResolver(
        EffectiveSubjectResolver subjects,
        SubjectGrantService grants,
        RoleRepository roles,
        StatementRepository statements
    ) {
        this.subjects = subjects;
        this.grants = grants;
        this.roles = roles;
        this.statements = statements;
    }

    /// Resolves direct subject Statements plus Statements reachable through bound Roles.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    @Override
    public List<EffectiveStatement> resolve(UUID principalId) {
        return this.resolveEntities(principalId)
            .stream()
            .map(entity -> new EffectiveStatement(entity.info(), entity.updatedAt()))
            .toList();
    }

    private List<StatementEntity> resolveEntities(UUID principalId) {
        Set<SubjectRef> effectiveSubjects = this.subjects.resolve(principalId);
        Set<UUID> statementIds = new HashSet<>();
        Set<UUID> roleIds = new HashSet<>();
        for (SubjectRef subject : effectiveSubjects) {
            statementIds.addAll(this.grants.statementIds(subject));
            roleIds.addAll(this.grants.roleIds(subject));
        }

        if (!roleIds.isEmpty()) {
            Set<UUID> reachableRoleIds = new HashSet<>(roleIds);
            reachableRoleIds.addAll(this.roles.findDescendantRoleIds(roleIds));
            for (RoleEntity role : this.roles.findDistinctByIdIn(reachableRoleIds)) {
                statementIds.addAll(role.statementIds());
            }
        }

        if (statementIds.isEmpty()) {
            return List.of();
        }
        return this.statements
            .findAllByIdIn(statementIds)
            .stream()
            .sorted((left, right) -> left.id().compareTo(right.id()))
            .toList();
    }
}
