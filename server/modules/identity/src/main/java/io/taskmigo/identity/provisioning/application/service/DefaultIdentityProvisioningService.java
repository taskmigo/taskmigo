package io.taskmigo.identity.provisioning.application.service;

import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditActor;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.membership.application.port.in.api.MembershipService;
import io.taskmigo.identity.provisioning.IdentityProvisioningException;
import io.taskmigo.identity.provisioning.IdentityProvisioningResult;
import io.taskmigo.identity.provisioning.application.port.in.api.IdentityProvisioningService;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.in.internal.UserDeletionLifecycleService;
import io.taskmigo.identity.user.application.port.in.internal.UserMutationResult;
import io.taskmigo.identity.user.application.port.out.UserAuditAppender;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.UserRuleViolation;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Reconciles managed Identity state through canonical User, Membership, and Access Control ports.
public final class DefaultIdentityProvisioningService implements IdentityProvisioningService {

    private static final String ENTITY_TYPE = "user";

    private final UserCommandService users;
    private final SubjectGrantAssignmentService grantAssignments;
    private final SubjectGrantQueryService grantQueries;
    private final MembershipService memberships;
    private final UserDeletionLifecycleService deletion;
    private final UserAuditAppender audits;
    private final TransactionRunner transactions;
    private final Clock clock;

    public DefaultIdentityProvisioningService(
        UserCommandService users,
        SubjectGrantAssignmentService grantAssignments,
        SubjectGrantQueryService grantQueries,
        MembershipService memberships,
        UserDeletionLifecycleService deletion,
        UserAuditAppender audits,
        TransactionRunner transactions,
        Clock clock
    ) {
        this.users = users;
        this.grantAssignments = grantAssignments;
        this.grantQueries = grantQueries;
        this.memberships = memberships;
        this.deletion = deletion;
        this.audits = audits;
        this.transactions = transactions;
        this.clock = clock;
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

        Set<UUID> beforeRoleIds = this.grantQueries.roleIds(IdentitySubjects.user(id));
        Set<UUID> beforeStatementIds = this.grantQueries.statementIds(IdentitySubjects.user(id));
        Set<UUID> beforeGroupIds = Set.copyOf(this.memberships.groupsForUser(id));

        boolean rolesChanged = !beforeRoleIds.equals(requestedRoleIds);
        boolean statementsChanged = !beforeStatementIds.isEmpty();
        boolean groupsChanged = !beforeGroupIds.equals(requestedGroupIds);

        List<AuditChange> changes = new ArrayList<>(mutation.changes());
        if (rolesChanged) {
            changes.add(AuditChange.visible("roleIds", ordered(beforeRoleIds), ordered(requestedRoleIds)));
            this.grantAssignments.setRoles(IdentitySubjects.user(id), requestedRoleIds);
        }
        if (statementsChanged) {
            changes.add(AuditChange.visible("statementIds", ordered(beforeStatementIds), List.of()));
            this.grantAssignments.setStatements(IdentitySubjects.user(id), Set.of());
        }
        if (groupsChanged) {
            changes.add(AuditChange.visible("groupIds", ordered(beforeGroupIds), ordered(requestedGroupIds)));
            this.memberships.setGroupsForUser(id, requestedGroupIds);
        }

        if (changes.isEmpty()) {
            return new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.UNCHANGED);
        }

        this.audits.append(
            new AuditEvent(UUID.randomUUID(), ENTITY_TYPE, id, this.systemActor(), this.clock.instant(), changes)
        );
        return new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.UPDATED);
    }

    private AuditActor systemActor() {
        User system = this.users
            .findByUsername(SystemUser.USERNAME)
            .orElseThrow(() ->
                new IllegalStateException("System User must exist before managed User updates are audited")
            );
        return new AuditActor(system.id(), system.username().value());
    }

    private boolean deleteUserInTransaction(String username) {
        User existing;
        try {
            existing = this.users.findByUsernameForUpdate(username).orElse(null);
        } catch (UserRuleViolation exception) {
            throw provisioningFailure(exception);
        }
        if (existing == null || existing.status() == UserStatus.RETAINED) {
            return false;
        }

        try {
            existing.requireManagedDeletionAllowed();
            User system = this.users
                .findByUsername(SystemUser.USERNAME)
                .orElseThrow(() ->
                    new IllegalStateException("System User must exist before managed User deletion is audited")
                );
            this.deletion.delete(
                existing,
                new UserMutationActor(system.id(), system.username().value()),
                this.clock.instant()
            );
        } catch (UserRuleViolation exception) {
            throw provisioningFailure(exception);
        }
        return true;
    }

    private static List<String> ordered(Collection<UUID> ids) {
        return ids.stream().sorted().map(UUID::toString).toList();
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
