package io.taskmigo.authorization.request.adapter.out.persistence;

import io.taskmigo.authorization.request.application.port.out.EffectiveStatement;
import io.taskmigo.authorization.request.application.port.out.EffectiveStatementResolver;
import io.taskmigo.authorization.role.application.port.out.RoleEffectiveStatementRepository;
import io.taskmigo.authorization.statement.adapter.out.persistence.StatementEntity;
import io.taskmigo.authorization.statement.adapter.out.persistence.StatementRepository;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.port.out.SubjectGrantRepository;
import io.taskmigo.authorization.subject.application.port.out.resolution.EffectiveSubjectResolver;
import io.taskmigo.authorization.subject.domain.SubjectGrants;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/// Resolves authoritative effective Statements through bounded persistence reads after resolving opaque subjects.
@Service
public class JpaEffectiveStatementResolver implements EffectiveStatementResolver {

    private final EffectiveSubjectResolver subjects;
    private final SubjectGrantRepository grants;
    private final RoleEffectiveStatementRepository roles;
    private final StatementRepository statements;

    JpaEffectiveStatementResolver(
        EffectiveSubjectResolver subjects,
        SubjectGrantRepository grants,
        RoleEffectiveStatementRepository roles,
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
            SubjectGrants directGrants = this.grants.load(subject);
            statementIds.addAll(directGrants.statementIds());
            roleIds.addAll(directGrants.roleIds());
        }

        statementIds.addAll(this.roles.statementIdsForRoles(roleIds));
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
