package io.taskmigo.identity.user.application;

import io.taskmigo.authorization.subject.SubjectGrantAssignmentService;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.GroupService;
import io.taskmigo.identity.membership.MembershipService;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.UserRegistrationService;
import io.taskmigo.identity.user.domain.UserRuleViolation;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Coordinates User creation with initial Access Control and Group assignments.
@Service
class UserRegistrationApplicationService implements UserRegistrationService {

    private final UserCommandService users;
    private final SubjectGrantAssignmentService grantAssignments;
    private final GroupService groups;
    private final MembershipService memberships;

    UserRegistrationApplicationService(
        UserCommandService users,
        SubjectGrantAssignmentService grantAssignments,
        GroupService groups,
        MembershipService memberships
    ) {
        this.users = users;
        this.grantAssignments = grantAssignments;
        this.groups = groups;
        this.memberships = memberships;
    }

    @Override
    @Transactional
    public UUID register(
        @Nullable String username,
        @Nullable Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable Collection<UUID> roleIds,
        @Nullable Collection<UUID> groupIds
    ) {
        Set<UUID> requestedRoleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        Set<UUID> requestedGroupIds = groupIds == null ? Set.of() : Set.copyOf(groupIds);
        this.groups.requireGroups(requestedGroupIds);

        UUID userId;
        try {
            userId = this.users.createRuntime(username, emails, firstName, lastName);
        } catch (UserRuleViolation exception) {
            throw new UserException(UserException.Type.INVALID_INPUT, exception.detail(), exception);
        }

        this.grantAssignments.setRoles(IdentitySubjects.user(userId), requestedRoleIds);
        this.memberships.setGroupsForUser(userId, requestedGroupIds);
        return userId;
    }
}
