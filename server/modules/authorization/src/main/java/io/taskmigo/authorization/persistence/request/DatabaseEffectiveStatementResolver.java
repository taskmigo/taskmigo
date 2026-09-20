package io.taskmigo.authorization.persistence.request;

import io.taskmigo.authorization.application.port.out.EffectiveSubjectResolver;
import io.taskmigo.authorization.role.application.RoleEffectiveStatementRepository;
import io.taskmigo.authorization.spi.EffectiveStatement;
import io.taskmigo.authorization.spi.EffectiveStatementResolver;
import io.taskmigo.authorization.statement.infrastructure.persistence.StatementEntity;
import io.taskmigo.authorization.statement.infrastructure.persistence.StatementRepository;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.SubjectGrantRepository;
import io.taskmigo.authorization.subject.domain.SubjectGrants;
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
    private final SubjectGrantRepository grants;
    private final RoleEffectiveStatementRepository roles;
    private final StatementRepository statements;

    DatabaseEffectiveStatementResolver(
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
