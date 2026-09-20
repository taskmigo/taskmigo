package io.taskmigo.identity.provisioning.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.subject.SubjectGrantAssignmentService;
import io.taskmigo.authorization.subject.SubjectGrantQueryService;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.membership.MembershipService;
import io.taskmigo.identity.provisioning.IdentityProvisioningException;
import io.taskmigo.identity.provisioning.IdentityProvisioningResult;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.in.internal.UserMutationResult;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.UserRuleViolation;
import io.taskmigo.identity.user.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultIdentityProvisioningServiceTest {

    /**
     * Verifies provisioning composes a newly created User with managed grants and memberships.
     *
     * Given: the shared User command path reports a created aggregate.
     * Expect: Roles, direct Statements, and Groups are reconciled and the result reports ADDED.
     */
    @Test
    @DisplayName("reports added when the shared user command path creates a user")
    void shouldReportAddedWhenCanonicalUserMutationCreatesUser() {
        // Arrange
        UUID id = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UserCommandService users = mock(UserCommandService.class);
        when(users.reconcileManaged("alice", "{bcrypt}hash", List.of("Alice@EXAMPLE.COM"), "Alice", "User")).thenReturn(
            new UserMutationResult(id, true, true)
        );
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        MembershipService groups = mock(MembershipService.class);
        var service = new DefaultIdentityProvisioningService(users, grantAssignments, grantQueries, groups);

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
    }

    /**
     * Verifies canonical User changes contribute to managed reconciliation status without duplicate normalization.
     *
     * Given: the shared User command path reports an existing changed aggregate and external assignments already match.
     * Expect: provisioning reports UPDATED without performing grant or membership writes.
     */
    @Test
    @DisplayName("reports updated when canonical user state changes")
    void shouldReportUpdatedWhenCanonicalUserMutationChangesExistingUser() {
        // Arrange
        UUID id = UUID.randomUUID();
        UserCommandService users = mock(UserCommandService.class);
        when(users.reconcileManaged(" alice ", null, List.of("ALICE@example.com"), " Alice ", " User ")).thenReturn(
            new UserMutationResult(id, false, true)
        );
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        when(grantQueries.roleIds(IdentitySubjects.user(id))).thenReturn(Set.of());
        when(grantQueries.statementIds(IdentitySubjects.user(id))).thenReturn(Set.of());
        MembershipService groups = mock(MembershipService.class);
        when(groups.groupsForUser(id)).thenReturn(List.of());
        var service = new DefaultIdentityProvisioningService(users, grantAssignments, grantQueries, groups);

        // Act
        IdentityProvisioningResult<UUID> result = service.reconcileUser(
            " alice ",
            null,
            List.of("ALICE@example.com"),
            " Alice ",
            " User ",
            Set.of(),
            Set.of()
        );

        // Assert
        assertThat(result).isEqualTo(new IdentityProvisioningResult<>(id, IdentityProvisioningResult.Change.UPDATED));
        verify(users).reconcileManaged(" alice ", null, List.of("ALICE@example.com"), " Alice ", " User ");
        verify(grantAssignments, never()).setRoles(any(), any());
        verify(grantAssignments, never()).setStatements(any(), any());
        verify(groups, never()).setGroupsForUser(any(), any());
    }

    /**
     * Verifies an identical managed User and identical external assignments remain unchanged.
     *
     * Given: canonical User state and all grants/memberships already equal desired state.
     * Expect: provisioning reports UNCHANGED and performs no mutation.
     */
    @Test
    @DisplayName("reports unchanged when managed user and assignments already match")
    void shouldReportUnchangedWhenManagedUserAlreadyMatches() {
        // Arrange
        UUID id = UUID.randomUUID();
        UserCommandService users = mock(UserCommandService.class);
        when(
            users.reconcileManaged("alice", "{bcrypt}different", List.of("alice@example.com"), "Alice", "User")
        ).thenReturn(new UserMutationResult(id, false, false));
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        when(grantQueries.roleIds(IdentitySubjects.user(id))).thenReturn(Set.of());
        when(grantQueries.statementIds(IdentitySubjects.user(id))).thenReturn(Set.of());
        MembershipService groups = mock(MembershipService.class);
        when(groups.groupsForUser(id)).thenReturn(List.of());
        var service = new DefaultIdentityProvisioningService(users, grantAssignments, grantQueries, groups);

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
    }

    /**
     * Verifies deletion clears external state before deleting the canonical User aggregate.
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
        when(users.findByUsername("alice")).thenReturn(Optional.of(existing));
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        MembershipService groups = mock(MembershipService.class);
        var service = new DefaultIdentityProvisioningService(users, grantAssignments, grantQueries, groups);

        // Act
        boolean removed = service.deleteUser("alice");

        // Assert
        assertThat(removed).isTrue();
        verify(grantAssignments).setRoles(IdentitySubjects.user(existing.id()), Set.of());
        verify(grantAssignments).setStatements(IdentitySubjects.user(existing.id()), Set.of());
        verify(groups).setGroupsForUser(existing.id(), Set.of());
        verify(users).delete(existing);
    }

    /**
     * Verifies deleting an absent managed User is idempotent.
     *
     * Given: the shared User command path cannot resolve the username.
     * Expect: deletion returns false and no external state is mutated.
     */
    @Test
    @DisplayName("does not remove anything when managed user is missing")
    void shouldNotDeleteManagedUserWhenUsernameIsMissing() {
        // Arrange
        UserCommandService users = mock(UserCommandService.class);
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        MembershipService groups = mock(MembershipService.class);
        var service = new DefaultIdentityProvisioningService(users, grantAssignments, grantQueries, groups);

        // Act
        boolean removed = service.deleteUser("alice");

        // Assert
        assertThat(removed).isFalse();
        verify(users, never()).delete(any());
        verify(grantAssignments, never()).setRoles(any(), any());
        verify(grantAssignments, never()).setStatements(any(), any());
        verify(groups, never()).setGroupsForUser(any(), any());
    }

    /**
     * Verifies the domain-owned system deletion rule is translated at the provisioning boundary.
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
        when(users.findByUsername("system")).thenReturn(Optional.of(system));
        SubjectGrantAssignmentService grantAssignments = mock(SubjectGrantAssignmentService.class);
        SubjectGrantQueryService grantQueries = mock(SubjectGrantQueryService.class);
        MembershipService groups = mock(MembershipService.class);
        var service = new DefaultIdentityProvisioningService(users, grantAssignments, grantQueries, groups);

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
     * Verifies the domain-owned system initial-password rule is translated at the provisioning boundary.
     *
     * Given: managed creation reports the missing-system-credential rule.
     * Expect: provisioning raises its typed failure.
     */
    @Test
    @DisplayName("translates missing system initial credential to provisioning failure")
    void shouldTranslateMissingSystemCredentialWhenManagedUserIsCreated() {
        // Arrange
        UserCommandService users = mock(UserCommandService.class);
        when(users.reconcileManaged("system", null, null, "System", "User")).thenThrow(systemCredentialFailure());
        var service = new DefaultIdentityProvisioningService(
            users,
            mock(SubjectGrantAssignmentService.class),
            mock(SubjectGrantQueryService.class),
            mock(MembershipService.class)
        );

        // Act + Assert
        assertThatThrownBy(() -> service.reconcileUser("system", null, null, "System", "User", Set.of(), Set.of()))
            .isInstanceOf(IdentityProvisioningException.class)
            .hasMessageContaining("initial password hash is required");
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
            "Test",
            "User",
            UserStatus.ACTIVE,
            username.equals("system") ? "{bcrypt}hash" : null
        );
    }
}
