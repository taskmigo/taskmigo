package io.taskmigo.identity.provisioning.internal;

import io.taskmigo.authorization.subject.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.SubjectGrantQueryService;
import io.taskmigo.foundation.ReconciliationAction;
import io.taskmigo.foundation.ReconciliationResult;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.membership.MembershipService;
import io.taskmigo.identity.provisioning.IdentityProvisioningException;
import io.taskmigo.identity.provisioning.IdentityProvisioningService;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.application.UserCommandService;
import io.taskmigo.identity.user.application.UserMutationResult;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.UserRuleViolation;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Reconciles managed Identity state through the same canonical User command path used by runtime registration.
@Service
class DefaultIdentityProvisioningService implements IdentityProvisioningService {

    private final UserCommandService users;
    private final SubjectGrantAssignmentService grantAssignments;
    private final SubjectGrantQueryService grantQueries;
    private final MembershipService memberships;

    DefaultIdentityProvisioningService(
        UserCommandService users,
        SubjectGrantAssignmentService grantAssignments,
        SubjectGrantQueryService grantQueries,
        MembershipService memberships
    ) {
        this.users = users;
        this.grantAssignments = grantAssignments;
        this.grantQueries = grantQueries;
        this.memberships = memberships;
    }

    @Override
    @Transactional
    public ReconciliationResult<UUID> reconcileUser(
        @Nullable String username,
        @Nullable String initialPasswordHash,
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        Collection<UUID> roleIds,
        Collection<UUID> groupIds
    ) {
        Set<UUID> requestedRoleIds = Set.copyOf(roleIds);
        Set<UUID> requestedGroupIds = Set.copyOf(groupIds);
        UserMutationResult mutation;
        try {
            mutation = this.users.reconcileManaged(username, initialPasswordHash, emails, firstName, lastName);
        } catch (UserRuleViolation exception) {
            throw provisioningFailure(exception);
        }

        UUID id = mutation.id();
        if (mutation.created()) {
            this.grantAssignments.setRoles(IdentitySubjects.user(id), requestedRoleIds);
            this.grantAssignments.setStatements(IdentitySubjects.user(id), Set.of());
            this.memberships.setGroupsForUser(id, requestedGroupIds);
            return new ReconciliationResult<>(id, ReconciliationAction.ADDED);
        }

        boolean rolesChanged = !this.grantQueries.roleIds(IdentitySubjects.user(id)).equals(requestedRoleIds);
        boolean statementsChanged = !this.grantQueries.statementIds(IdentitySubjects.user(id)).isEmpty();
        boolean groupsChanged = !Set.copyOf(this.memberships.groupsForUser(id)).equals(requestedGroupIds);
        if (!mutation.changed() && !rolesChanged && !statementsChanged && !groupsChanged) {
            return new ReconciliationResult<>(id, ReconciliationAction.UNCHANGED);
        }
        if (rolesChanged) {
            this.grantAssignments.setRoles(IdentitySubjects.user(id), requestedRoleIds);
        }
        if (statementsChanged) {
            this.grantAssignments.setStatements(IdentitySubjects.user(id), Set.of());
        }
        if (groupsChanged) {
            this.memberships.setGroupsForUser(id, requestedGroupIds);
        }
        return new ReconciliationResult<>(id, ReconciliationAction.UPDATED);
    }

    @Override
    @Transactional
    public boolean deleteUser(String username) {
        User existing;
        try {
            existing = this.users.findByUsername(username).orElse(null);
        } catch (UserRuleViolation exception) {
            throw provisioningFailure(exception);
        }
        if (existing == null) {
            return false;
        }
        try {
            existing.requireManagedDeletionAllowed();
        } catch (UserRuleViolation exception) {
            throw provisioningFailure(exception);
        }

        this.grantAssignments.setRoles(IdentitySubjects.user(existing.id()), Set.of());
        this.grantAssignments.setStatements(IdentitySubjects.user(existing.id()), Set.of());
        this.memberships.setGroupsForUser(existing.id(), Set.of());
        this.users.delete(existing);
        return true;
    }

    private static RuntimeException provisioningFailure(UserRuleViolation exception) {
        if (
            exception.reason() == UserRuleViolation.Reason.SYSTEM_INITIAL_PASSWORD_REQUIRED ||
            exception.reason() == UserRuleViolation.Reason.SYSTEM_USER_DELETION_FORBIDDEN
        ) {
            return new IdentityProvisioningException(exception.detail());
        }
        return new UserException(UserException.Type.BAD_REQUEST, exception.detail(), exception);
    }
}
