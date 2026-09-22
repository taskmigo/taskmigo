package io.taskmigo.identity.adapter.out.accesscontrol;

import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.port.out.resolution.EffectiveSubjectResolver;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.GroupException;
import io.taskmigo.identity.group.application.port.out.GroupHierarchyRepository;
import io.taskmigo.identity.group.application.port.out.GroupQueryRepository;
import io.taskmigo.identity.membership.application.port.out.MembershipRepository;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.application.port.out.UserQueryRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Adapts Identity membership and hierarchy state to Access Control's effective-subject outbound port.
@Service
public class IdentityEffectiveSubjectResolver implements EffectiveSubjectResolver {

    private final UserQueryRepository users;
    private final GroupQueryRepository groups;
    private final MembershipRepository memberships;
    private final GroupHierarchyRepository hierarchies;

    public IdentityEffectiveSubjectResolver(
        UserQueryRepository users,
        GroupQueryRepository groups,
        MembershipRepository memberships,
        GroupHierarchyRepository hierarchies
    ) {
        this.users = users;
        this.groups = groups;
        this.memberships = memberships;
        this.hierarchies = hierarchies;
    }

    /// Resolves a User subject together with every direct and inherited Group subject effective for that User.
    @Transactional(readOnly = true)
    @Override
    public Set<SubjectRef> resolve(UUID principalId) {
        if (!this.users.exists(principalId)) {
            throw new UserException(UserException.Type.NOT_FOUND, "User not found");
        }

        LinkedHashSet<SubjectRef> subjects = new LinkedHashSet<>();
        subjects.add(IdentitySubjects.user(principalId));
        List<UUID> directGroupIds = this.memberships.groupsForUser(principalId);
        if (!directGroupIds.isEmpty()) {
            this.hierarchies
                .descendantGroupIds(directGroupIds)
                .forEach(groupId -> subjects.add(IdentitySubjects.group(groupId)));
        }
        return Set.copyOf(subjects);
    }

    /// Expands Identity-owned subjects while treating subjects owned by other contexts as leaves.
    @Transactional(readOnly = true)
    @Override
    public Set<SubjectRef> expand(SubjectRef subject) {
        if (IdentitySubjects.isUser(subject)) {
            return this.resolve(subject.id());
        }
        if (!IdentitySubjects.isGroup(subject)) {
            return Set.of(subject);
        }
        if (!this.groups.exists(subject.id())) {
            throw new GroupException(GroupException.Type.NOT_FOUND, "Group not found");
        }

        LinkedHashSet<SubjectRef> subjects = new LinkedHashSet<>();
        this.hierarchies
            .descendantGroupIds(Set.of(subject.id()))
            .forEach(groupId -> subjects.add(IdentitySubjects.group(groupId)));
        return Set.copyOf(subjects);
    }
}
