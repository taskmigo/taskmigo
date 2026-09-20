package io.taskmigo.identity.user.application;

import io.taskmigo.authorization.role.RoleService;
import io.taskmigo.authorization.subject.SubjectGrantService;
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
    private final RoleService roles;
    private final SubjectGrantService grants;
    private final GroupService groups;
    private final MembershipService memberships;

    UserRegistrationApplicationService(
        UserCommandService users,
        RoleService roles,
        SubjectGrantService grants,
        GroupService groups,
        MembershipService memberships
    ) {
        this.users = users;
        this.roles = roles;
        this.grants = grants;
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
        this.roles.requireRoles(requestedRoleIds);
        this.groups.requireGroups(requestedGroupIds);

        UUID userId;
        try {
            userId = this.users.createRuntime(username, emails, firstName, lastName);
        } catch (UserRuleViolation exception) {
            throw new UserException(UserException.Type.BAD_REQUEST, exception.detail(), exception);
        }

        this.grants.setRoles(IdentitySubjects.user(userId), requestedRoleIds);
        this.memberships.setGroupsForUser(userId, requestedGroupIds);
        return userId;
    }
}
