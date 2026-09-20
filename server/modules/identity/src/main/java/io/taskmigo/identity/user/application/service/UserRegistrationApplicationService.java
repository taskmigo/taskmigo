package io.taskmigo.identity.user.application.service;

import io.taskmigo.authorization.subject.SubjectGrantAssignmentService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.GroupService;
import io.taskmigo.identity.membership.MembershipService;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.application.port.in.api.UserRegistrationService;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.domain.UserRuleViolation;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Coordinates User creation with initial Access Control and Group assignments as one application transaction.
public final class UserRegistrationApplicationService implements UserRegistrationService {

    private final UserCommandService users;
    private final SubjectGrantAssignmentService grantAssignments;
    private final GroupService groups;
    private final MembershipService memberships;
    private final TransactionRunner transactions;

    public UserRegistrationApplicationService(
        UserCommandService users,
        SubjectGrantAssignmentService grantAssignments,
        GroupService groups,
        MembershipService memberships,
        TransactionRunner transactions
    ) {
        this.users = users;
        this.grantAssignments = grantAssignments;
        this.groups = groups;
        this.memberships = memberships;
        this.transactions = transactions;
    }

    @Override
    public UUID register(
        @Nullable String username,
        @Nullable Set<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable Collection<UUID> roleIds,
        @Nullable Collection<UUID> groupIds
    ) {
        return this.transactions.write(() -> {
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
        });
    }
}
