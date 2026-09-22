package io.taskmigo.authorization.subject.application;

import io.taskmigo.authorization.application.port.out.EffectiveSubjectResolver;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.application.port.in.api.RoleService;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.SubjectRoleQueryService;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Resolves effective Role projections while keeping Role semantics inside Access Control.
@Service
class DefaultSubjectRoleQueryService implements SubjectRoleQueryService {

    private final EffectiveSubjectResolver subjects;
    private final SubjectGrantRepository grants;
    private final RoleService roles;

    DefaultSubjectRoleQueryService(
        EffectiveSubjectResolver subjects,
        SubjectGrantRepository grants,
        RoleService roles
    ) {
        this.subjects = subjects;
        this.grants = grants;
        this.roles = roles;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRoles(SubjectRef subject) {
        return this.effectiveRoles(this.subjects.expand(subject));
    }

    private List<RoleInfo> effectiveRoles(Collection<SubjectRef> subjects) {
        Set<UUID> roleIds = new LinkedHashSet<>();
        for (SubjectRef subject : subjects) {
            roleIds.addAll(this.grants.load(subject).roleIds());
        }
        return this.roles.effectiveRoles(roleIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleInfo> effectiveRolesForPrincipal(UUID principalId) {
        return this.effectiveRoles(this.subjects.resolve(principalId));
    }
}
