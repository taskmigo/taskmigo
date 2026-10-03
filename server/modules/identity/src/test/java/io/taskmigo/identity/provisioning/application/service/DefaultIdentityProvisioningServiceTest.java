package io.taskmigo.identity.provisioning.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantQueryService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.membership.application.port.in.api.MembershipService;
import io.taskmigo.identity.provisioning.IdentityProvisioningException;
import io.taskmigo.identity.provisioning.IdentityProvisioningResult;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.in.internal.UserDeletionLifecycleService;
import io.taskmigo.identity.user.application.port.in.internal.UserMutationResult;
import io.taskmigo.identity.user.application.port.out.UserAuditAppender;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.UserRuleViolation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@NullMarked
class DefaultIdentityProvisioningServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T00:00:00Z");

    /**
     * Verifies that provisioning composes a newly created User with managed grants and memberships.
     *
     * Given: the shared User command path reports a created aggregate.
     * Expect: Roles, direct Statements, and Groups are reconciled without emitting an update audit event.
     */
    @Test
    @DisplayName("reports added when the shared user command path creates a user")
    void shouldReportAddedWhenCanonicalUserMutationCreatesUser() {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UserCommandService users = mock(UserCommandService.class);
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        when(users.reconcileManaged("alice", "{bcrypt}hash", List.of("Alice@EXAMPLE.COM"), "Alice", "User")).thenReturn(
            new UserMutationResult(id, true, true)
        );
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        MembershipService groups = mock(MembershipService.class);
        UserAuditAppender audits = mock(UserAuditAppender.class);
        var service = service(users, grantAssignments, grantQueries, groups, audits);

        // Act
        IdentityProvisioningResult<UUID> result = service.reconcileUser(
            "alice",
            "{bcrypt}hash",
            List.of("Alice@EXAMPLE.COM"),
            "Alice",
            "User",
            Set.of(roleId),
            Set.of(groupId)
        );

        // Assert
        assertThat(result).isEqualTo(new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.CREATED));
        verify(grantAssignments).setRoles(IdentitySubjects.user(id), Set.of(roleId));
        verify(grantAssignments).setStatements(IdentitySubjects.user(id), Set.of());
        verify(groups).setGroupsForUser(id, Set.of(groupId));
        verify(audits, never()).append(any());
    }

    /**
     * Verifies that one managed reconciliation emits one complete audit event for every changed User field.
     *
     * Given: canonical profile/credential changes plus Role, Statement, and Group assignment changes.
     * Expect: provisioning writes desired state and appends one event containing all field-level diffs.
     */
    @Test
    @DisplayName("audits all changes from one managed user reconciliation")
    void shouldPublishOneAuditEventWhenManagedUserChangesAcrossBoundaries() {
        // Arrange
        User existing = user("alice");
        User system = user("system");
        UUID id = existing.id();
        UUID oldRole = UUID.randomUUID();
        UUID newRole = UUID.randomUUID();
        UUID oldStatement = UUID.randomUUID();
        UUID oldGroup = UUID.randomUUID();
        UUID newGroup = UUID.randomUUID();
        UserCommandService users = mock(UserCommandService.class);
        when(users.findByUsername(SystemUser.USERNAME)).thenReturn(Optional.of(system));
        when(users.reconcileManaged("alice", "{bcrypt}initial", List.of("new@example.com"), "New", "User")).thenReturn(
            new UserMutationResult(
                id,
                false,
                true,
                List.of(AuditChange.visible("firstName", "Test", "New"), AuditChange.sensitive("passwordHash"))
            )
        );
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        when(grantQueries.roleIds(IdentitySubjects.user(id))).thenReturn(Set.of(oldRole));
        when(grantQueries.statementIds(IdentitySubjects.user(id))).thenReturn(Set.of(oldStatement));
        MembershipService groups = mock(MembershipService.class);
        when(groups.groupsForUser(id)).thenReturn(List.of(oldGroup));
        UserAuditAppender audits = mock(UserAuditAppender.class);
        var service = service(users, grantAssignments, grantQueries, groups, audits);
        ArgumentCaptor<AuditEvent> event = ArgumentCaptor.forClass(AuditEvent.class);

        // Act
        IdentityProvisioningResult<UUID> result = service.reconcileUser(
            "alice",
            "{bcrypt}initial",
            List.of("new@example.com"),
            "New",
            "User",
            Set.of(newRole),
            Set.of(newGroup)
        );

        // Assert
        assertThat(result).isEqualTo(new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.UPDATED));
        verify(grantAssignments).setRoles(IdentitySubjects.user(id), Set.of(newRole));
        verify(grantAssignments).setStatements(IdentitySubjects.user(id), Set.of());
        verify(groups).setGroupsForUser(id, Set.of(newGroup));
        verify(audits).append(event.capture());
        assertThat(event.getValue().entityType()).isEqualTo("user");
        assertThat(event.getValue().entityId()).isEqualTo(id);
        assertThat(event.getValue().actor().id()).isEqualTo(system.id());
        assertThat(event.getValue().actor().username()).isEqualTo(SystemUser.USERNAME);
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
        assertThat(event.getValue().changes())
            .extracting(AuditChange::field)
            .containsExactly("firstName", "passwordHash", "roleIds", "statementIds", "groupIds");
        assertThat(event.getValue().changes())
            .filteredOn(AuditChange::sensitive)
            .singleElement()
            .extracting(AuditChange::field)
            .isEqualTo("passwordHash");
    }

    /**
     * Verifies that an identical managed User and identical external assignments remain unchanged.
     *
     * Given: canonical User state and all grants/memberships already equal desired state.
     * Expect: provisioning reports UNCHANGED and appends no audit event.
     */
    @Test
    @DisplayName("reports unchanged when managed user and assignments already match")
    void shouldReportUnchangedWhenManagedUserAlreadyMatches() {
        // Arrange
        User existing = user("alice");
        UUID id = existing.id();
        UserCommandService users = mock(UserCommandService.class);
        when(users.findByUsername("alice")).thenReturn(Optional.of(existing));
        when(
            users.reconcileManaged("alice", "{bcrypt}different", List.of("alice@example.com"), "Alice", "User")
        ).thenReturn(new UserMutationResult(id, false, false));
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        when(grantQueries.roleIds(IdentitySubjects.user(id))).thenReturn(Set.of());
        when(grantQueries.statementIds(IdentitySubjects.user(id))).thenReturn(Set.of());
        MembershipService groups = mock(MembershipService.class);
        when(groups.groupsForUser(id)).thenReturn(List.of());
        UserAuditAppender audits = mock(UserAuditAppender.class);
        var service = service(users, grantAssignments, grantQueries, groups, audits);

        // Act
        IdentityProvisioningResult<UUID> result = service.reconcileUser(
            "alice",
            "{bcrypt}different",
            List.of("alice@example.com"),
            "Alice",
            "User",
            Set.of(),
            Set.of()
        );

        // Assert
        assertThat(result).isEqualTo(new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.UNCHANGED));
        verify(grantAssignments, never()).setRoles(any(), any());
        verify(grantAssignments, never()).setStatements(any(), any());
        verify(groups, never()).setGroupsForUser(any(), any());
        verify(audits, never()).append(any());
    }

    /**
     * Verifies that deletion clears external state before deleting the canonical User aggregate.
     *
     * Given: a non-system managed User resolved by the shared User command path.
     * Expect: grants and memberships are cleared, then the aggregate is deleted and removal is reported.
     */
    @Test
    @DisplayName("deletes an existing managed user through the shared command path")
    void shouldDeleteManagedUserWhenExistingUserIsRemovable() {
        // Arrange
        UserCommandService users = mock(UserCommandService.class);
        User existing = user("alice");
        User system = user("system");
        when(users.findByUsernameForUpdate("alice")).thenReturn(Optional.of(existing));
        when(users.findByUsername(SystemUser.USERNAME)).thenReturn(Optional.of(system));
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        MembershipService groups = mock(MembershipService.class);
        UserDeletionLifecycleService deletion = mock(UserDeletionLifecycleService.class);
        var service = service(
            users,
            grantAssignments,
            grantQueries,
            groups,
            mock(UserAuditAppender.class),
            deletion
        );

        // Act
        boolean removed = service.deleteUser("alice");

        // Assert
        assertThat(removed).isTrue();
        verify(deletion).delete(
            existing,
            new UserMutationActor(system.id(), SystemUser.USERNAME),
            NOW
        );
    }

    /**
     * Verifies that deleting an absent managed User is idempotent.
     *
     * Given: the shared User command path cannot resolve the username.
     * Expect: deletion returns false and no external state is mutated.
     */
    @Test
    @DisplayName("does not remove anything when managed user is missing")
    void shouldNotDeleteManagedUserWhenUsernameIsMissing() {
        // Arrange
        UserCommandService users = mock(UserCommandService.class);
        when(users.findByUsernameForUpdate("alice")).thenReturn(Optional.empty());
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        MembershipService groups = mock(MembershipService.class);
        var service = service(users, grantAssignments, grantQueries, groups, mock(UserAuditAppender.class));

        // Act
        boolean removed = service.deleteUser("alice");

        // Assert
        assertThat(removed).isFalse();
        verify(grantAssignments, never()).setRoles(any(), any());
        verify(grantAssignments, never()).setStatements(any(), any());
        verify(groups, never()).setGroupsForUser(any(), any());
    }

    /**
     * Verifies that the domain-owned system deletion rule is translated at the provisioning boundary.
     *
     * Given: the shared User command path resolves the system User.
     * Expect: provisioning raises its typed failure and performs no delete or cleanup.
     */
    @Test
    @DisplayName("translates the system user deletion rule to provisioning failure")
    void shouldRejectManagedDeletionWhenUserIsSystem() {
        // Arrange
        UserCommandService users = mock(UserCommandService.class);
        User system = user("system");
        when(users.findByUsernameForUpdate("system")).thenReturn(Optional.of(system));
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        MembershipService groups = mock(MembershipService.class);
        var service = service(users, grantAssignments, grantQueries, groups, mock(UserAuditAppender.class));

        // Act + Assert
        assertThatThrownBy(() -> service.deleteUser("system"))
            .isInstanceOf(IdentityProvisioningException.class)
            .hasMessageContaining("system user cannot be deleted");
        verify(users, never()).delete(any());
        verify(grantAssignments, never()).setRoles(any(), any());
        verify(grantAssignments, never()).setStatements(any(), any());
        verify(groups, never()).setGroupsForUser(any(), any());
    }

    /**
     * Verifies that the domain-owned system initial-password rule is translated at the provisioning boundary.
     *
     * Given: managed creation reports the missing-system-credential rule.
     * Expect: provisioning raises its typed failure.
     */
    @Test
    @DisplayName("translates missing system initial credential to provisioning failure")
    void shouldTranslateMissingSystemCredentialWhenManagedUserIsCreated() {
        // Arrange
        UserCommandService users = mock(UserCommandService.class);
        when(users.findByUsername("system")).thenReturn(Optional.empty());
        when(users.reconcileManaged("system", null, null, "System", "User")).thenThrow(systemCredentialFailure());
        var service = service(
            users,
            mock(SubjectGrantAssignmentService.class),
            mock(SubjectGrantQueryService.class),
            mock(MembershipService.class),
            mock(UserAuditAppender.class)
        );

        // Act + Assert
        assertThatThrownBy(() -> service.reconcileUser("system", null, null, "System", "User", Set.of(), Set.of()))
            .isInstanceOf(IdentityProvisioningException.class)
            .hasMessageContaining("initial password hash is required");
    }

    private static DefaultIdentityProvisioningService service(
        UserCommandService users,
        SubjectGrantAssignmentService grantAssignments,
        SubjectGrantQueryService grantQueries,
        MembershipService groups,
        UserAuditAppender audits
    ) {
        return service(
            users,
            grantAssignments,
            grantQueries,
            groups,
            audits,
            mock(UserDeletionLifecycleService.class)
        );
    }

    private static DefaultIdentityProvisioningService service(
        UserCommandService users,
        SubjectGrantAssignmentService grantAssignments,
        SubjectGrantQueryService grantQueries,
        MembershipService groups,
        UserAuditAppender audits,
        UserDeletionLifecycleService deletion
    ) {
        return new DefaultIdentityProvisioningService(
            users,
            grantAssignments,
            grantQueries,
            groups,
            deletion,
            audits,
            directTransactions(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static UserRuleViolation systemCredentialFailure() {
        try {
            User.managed(UUID.randomUUID(), "system", null, Set.of(), "System", "User");
        } catch (UserRuleViolation exception) {
            return exception;
        }
        throw new AssertionError("Expected the system credential invariant to fail");
    }

    private static User user(String username) {
        return User.restore(
            UUID.randomUUID(),
            username,
            Set.of(),
            username.equals("alice") ? "Test" : "System",
            "User",
            UserStatus.ACTIVE,
            username.equals("system") ? "{bcrypt}hash" : null
        );
    }

    private static TransactionRunner directTransactions() {
        return new TransactionRunner() {
            @Override
            public <T> T read(Supplier<T> work) {
                return work.get();
            }

            @Override
            public <T> T write(Supplier<T> work) {
                return work.get();
            }

            @Override
            public void write(Runnable work) {
                work.run();
            }
        };
    }
}
