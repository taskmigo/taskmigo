package io.taskmigo.identity.persistence.integration;

import io.taskmigo.authorization.spi.EffectiveSubjectResolver;
import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.application.GroupHierarchyRepository;
import io.taskmigo.identity.membership.application.MembershipRepository;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.application.UserQueryRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Adapts Identity membership and Group hierarchy read ports to Access Control subjects.
@Service
public class IdentityEffectiveSubjectResolver implements EffectiveSubjectResolver {

    private final UserQueryRepository users;
    private final MembershipRepository memberships;
    private final GroupHierarchyRepository hierarchies;

    public IdentityEffectiveSubjectResolver(
        UserQueryRepository users,
        MembershipRepository memberships,
        GroupHierarchyRepository hierarchies
    ) {
        this.users = users;
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
}
