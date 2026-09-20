package io.taskmigo.identity.provisioning.application.service;

import io.taskmigo.authorization.subject.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.SubjectGrantQueryService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.membership.application.port.in.api.MembershipService;
import io.taskmigo.identity.provisioning.IdentityProvisioningException;
import io.taskmigo.identity.provisioning.IdentityProvisioningResult;
import io.taskmigo.identity.provisioning.application.port.in.api.IdentityProvisioningService;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.in.internal.UserMutationResult;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.UserRuleViolation;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Reconciles managed Identity state through canonical User, Membership, and Access Control ports.
public final class DefaultIdentityProvisioningService implements IdentityProvisioningService {

    private final UserCommandService users;
    private final SubjectGrantAssignmentService grantAssignments;
    private final SubjectGrantQueryService grantQueries;
    private final MembershipService memberships;
    private final TransactionRunner transactions;

    public DefaultIdentityProvisioningService(
        UserCommandService users,
        SubjectGrantAssignmentService grantAssignments,
        SubjectGrantQueryService grantQueries,
        MembershipService memberships,
        TransactionRunner transactions
    ) {
        this.users = users;
        this.grantAssignments = grantAssignments;
        this.grantQueries = grantQueries;
        this.memberships = memberships;
        this.transactions = transactions;
    }

    @Override
    public IdentityProvisioningResult<UUID> reconcileUser(
        @Nullable String username,
        @Nullable String initialPasswordHash,
        @Nullable Collection<String> emails,
        @Nullable String firstName,
        @Nullable String lastName,
        Collection<UUID> roleIds,
        Collection<UUID> groupIds
    ) {
        return this.transactions.write(() ->
            this.reconcileUserInTransaction(
                username,
                initialPasswordHash,
                emails,
                firstName,
                lastName,
                roleIds,
                groupIds
            )
        );
    }

    @Override
    public boolean deleteUser(String username) {
        return this.transactions.write(() -> this.deleteUserInTransaction(username));
    }

    private IdentityProvisioningResult<UUID> reconcileUserInTransaction(
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
            return new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.CREATED);
        }

        boolean rolesChanged = !this.grantQueries.roleIds(IdentitySubjects.user(id)).equals(requestedRoleIds);
        boolean statementsChanged = !this.grantQueries.statementIds(IdentitySubjects.user(id)).isEmpty();
        boolean groupsChanged = !Set.copyOf(this.memberships.groupsForUser(id)).equals(requestedGroupIds);
        if (!mutation.changed() && !rolesChanged && !statementsChanged && !groupsChanged) {
            return new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.UNCHANGED);
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
        return new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.UPDATED);
    }

    private boolean deleteUserInTransaction(String username) {
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
        return new UserException(UserException.Type.INVALID_INPUT, exception.detail(), exception);
    }
}
