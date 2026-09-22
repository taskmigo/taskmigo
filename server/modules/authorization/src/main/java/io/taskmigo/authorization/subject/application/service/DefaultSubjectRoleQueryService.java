package io.taskmigo.authorization.subject.application.service;

import io.taskmigo.authorization.application.port.out.transaction.TransactionRunner;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.application.port.in.api.RoleService;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectRoleQueryService;
import io.taskmigo.authorization.subject.application.port.out.SubjectGrantRepository;
import io.taskmigo.authorization.subject.application.port.out.resolution.EffectiveSubjectResolver;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// Resolves effective Role projections while keeping Role semantics inside Access Control.
public final class DefaultSubjectRoleQueryService implements SubjectRoleQueryService {

    private final EffectiveSubjectResolver subjects;
    private final SubjectGrantRepository grants;
    private final RoleService roles;
    private final TransactionRunner transactions;

    public DefaultSubjectRoleQueryService(
        EffectiveSubjectResolver subjects,
        SubjectGrantRepository grants,
        RoleService roles,
        TransactionRunner transactions
    ) {
        this.subjects = subjects;
        this.grants = grants;
        this.roles = roles;
        this.transactions = transactions;
    }

    @Override
    public List<RoleInfo> effectiveRoles(SubjectRef subject) {
        return this.transactions.read(() -> this.effectiveRolesDirect(this.subjects.expand(subject)));
    }

    @Override
    public List<RoleInfo> effectiveRolesForPrincipal(UUID principalId) {
        return this.transactions.read(() -> this.effectiveRolesDirect(this.subjects.resolve(principalId)));
    }

    private List<RoleInfo> effectiveRolesDirect(Collection<SubjectRef> subjects) {
        Set<UUID> roleIds = new LinkedHashSet<>();
        for (SubjectRef subject : subjects) {
            roleIds.addAll(this.grants.load(subject).roleIds());
        }
        return this.roles.effectiveRoles(roleIds);
    }
}
