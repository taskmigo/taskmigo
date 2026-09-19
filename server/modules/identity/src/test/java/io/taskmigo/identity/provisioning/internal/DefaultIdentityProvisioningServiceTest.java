package io.taskmigo.identity.provisioning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.authorization.subject.SubjectGrantService;
import io.taskmigo.foundation.ReconciliationAction;
import io.taskmigo.foundation.ReconciliationResult;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.group.GroupService;
import io.taskmigo.identity.provisioning.IdentityProvisioningException;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.internal.UserStore;
import io.taskmigo.identity.user.internal.UserStore.UserState;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class DefaultIdentityProvisioningServiceTest {

    /**
     * Verifies that reconciling a missing managed User persists the supplied initial password hash.
     *
     * Given: a valid User with an initial encoded password and no persisted User with that username.
     * Expect: one User is created with the supplied hash and the result reports ADDED.
     */
    @Test
    @DisplayName("reports an added action when a managed User is missing")
    void shouldReportAddedActionWhenManagedUserIsMissing() {
        // Arrange
        UserStore users = mock(UserStore.class);
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        var service = service(users);
        ArgumentCaptor<UserState> state = ArgumentCaptor.forClass(UserState.class);

        // Act
        ReconciliationResult<UUID> result = service.reconcileUser(
            "alice",
            "{bcrypt}initial-hash",
            List.of(),
            "Alice",
            "User",
            Set.of(),
            Set.of()
        );

        // Assert
        assertThat(result.action()).isEqualTo(ReconciliationAction.ADDED);
        verify(users).create(state.capture());
        assertThat(state.getValue().passwordHash()).isEqualTo("{bcrypt}initial-hash");
    }

    /**
     * Verifies that reconciling an existing managed User updates non-credential desired state.
     *
     * Given: an existing User with a changed profile and an initialized password hash.
     * Expect: the profile changes while the existing credential is left untouched.
     */
    @Test
    @DisplayName("reports an updated action when an existing managed User profile changes")
    void shouldReportUpdatedActionWhenManagedUserProfileChanges() {
        // Arrange
        UUID id = UUID.randomUUID();
        UserStore users = mock(UserStore.class);
        when(users.findByUsername("alice")).thenReturn(
            Optional.of(new UserState(id, "alice", Set.of(), "Old", "User", true, "{bcrypt}existing-hash"))
        );
        var service = service(users);

        // Act
        ReconciliationResult<UUID> result = service.reconcileUser(
            "alice",
            null,
            List.of("alice@example.com"),
            "Alice",
            "User",
            Set.of(),
            Set.of()
        );

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.UPDATED));
        verify(users).updateProfile(id, Set.of("alice@example.com"), "Alice", "User");
        verify(users, Mockito.never()).updatePasswordHash(Mockito.any(), Mockito.any());
    }

    /**
     * Verifies that an initial password is not a continuously reconciled User field.
     *
     * Given: an existing User that already has a password hash and a different initial hash in migration input.
     * Expect: reconciliation reports UNCHANGED and does not replace the persisted password hash.
     */
    @Test
    @DisplayName("preserves an existing password when migration supplies another initial password")
    void shouldPreserveExistingPasswordWhenInitialPasswordIsSuppliedAgain() {
        // Arrange
        UUID id = UUID.randomUUID();
        UserStore users = mock(UserStore.class);
        when(users.findByUsername("alice")).thenReturn(
            Optional.of(
                new UserState(id, "alice", Set.of("alice@example.com"), "Alice", "User", true, "{bcrypt}existing-hash")
            )
        );
        SubjectGrantService grants = mock(SubjectGrantService.class);
        when(grants.roleIds(IdentitySubjects.user(id))).thenReturn(Set.of());
        when(grants.statementIds(IdentitySubjects.user(id))).thenReturn(Set.of());
        GroupService groups = mock(GroupService.class);
        when(groups.groupsForUser(id)).thenReturn(List.of());
        var service = service(users, grants, groups);

        // Act
        ReconciliationResult<UUID> result = service.reconcileUser(
            "alice",
            "{bcrypt}different-initial-hash",
            List.of("alice@example.com"),
            "Alice",
            "User",
            Set.of(),
            Set.of()
        );

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.UNCHANGED));
        verify(users, Mockito.never()).updatePasswordHash(Mockito.any(), Mockito.any());
        verify(users, Mockito.never()).updateProfile(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        verify(grants, Mockito.never()).setRoles(Mockito.any(), Mockito.any());
        verify(groups, Mockito.never()).setGroupsForUser(Mockito.any(), Mockito.any());
    }

    /**
     * Verifies that migration can initialize a credential for a managed User that has none.
     *
     * Given: an existing User with no password hash and a non-blank initial password hash.
     * Expect: the password hash is initialized once and reconciliation reports UPDATED.
     */
    @Test
    @DisplayName("initializes a missing password when an initial password is available")
    void shouldInitializePasswordWhenManagedUserHasNoCredential() {
        // Arrange
        UUID id = UUID.randomUUID();
        UserStore users = mock(UserStore.class);
        when(users.findByUsername("alice")).thenReturn(
            Optional.of(new UserState(id, "alice", Set.of(), "Alice", "User", true, null))
        );
        var service = service(users);

        // Act
        ReconciliationResult<UUID> result = service.reconcileUser(
            "alice",
            "{bcrypt}initial-hash",
            List.of(),
            "Alice",
            "User",
            Set.of(),
            Set.of()
        );

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.UPDATED));
        verify(users).updatePasswordHash(id, "{bcrypt}initial-hash");
    }

    /**
     * Verifies that deleting an existing managed User reports a removal.
     *
     * Given: a non-system User found by username.
     * Expect: grants and group memberships are cleared, the User is deleted, and the operation returns true.
     */
    @Test
    @DisplayName("reports removal when an existing managed User is deleted")
    void shouldReportRemovalWhenManagedUserExists() {
        // Arrange
        UUID id = UUID.randomUUID();
        UserStore users = mock(UserStore.class);
        when(users.findByUsername("alice")).thenReturn(
            Optional.of(new UserState(id, "alice", Set.of(), "Alice", "User", true, null))
        );
        var service = service(users);

        // Act
        boolean removed = service.deleteUser("alice");

        // Assert
        assertThat(removed).isTrue();
        verify(users).delete(id);
    }

    /**
     * Verifies that deleting a missing managed User does not report a removal.
     *
     * Given: a username that is absent from persistence.
     * Expect: deletion returns false and no User delete operation is attempted.
     */
    @Test
    @DisplayName("does not report removal when a managed User is missing")
    void shouldNotReportRemovalWhenManagedUserIsMissing() {
        // Arrange
        UserStore users = mock(UserStore.class);
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        var service = service(users);

        // Act
        boolean removed = service.deleteUser("alice");

        // Assert
        assertThat(removed).isFalse();
        verify(users, Mockito.never()).delete(ArgumentMatchers.any());
    }

    /**
     * Verifies that managed reconciliation cannot delete the system User.
     *
     * Given: the persisted managed User has the reserved system username.
     * Expect: deletion raises a typed provisioning failure and leaves the User store untouched.
     */
    @Test
    @DisplayName("rejects removal of the managed system User")
    void shouldRejectRemovalWhenManagedUserIsSystem() {
        // Arrange
        UUID id = UUID.randomUUID();
        UserStore users = mock(UserStore.class);
        when(users.findByUsername(SystemUser.USERNAME)).thenReturn(
            Optional.of(new UserState(id, SystemUser.USERNAME, Set.of(), "System", "User", true, "{bcrypt}hash"))
        );
        var service = service(users);

        // Act + Assert
        assertThatThrownBy(() -> service.deleteUser(SystemUser.USERNAME))
            .isInstanceOf(IdentityProvisioningException.class)
            .hasMessageContaining("system user cannot be deleted");
        verify(users, Mockito.never()).delete(Mockito.any());
    }

    /**
     * Verifies that a new system User cannot be provisioned without an initial credential.
     *
     * Given: no existing system User and a null initial password hash.
     * Expect: the provisioning boundary raises a typed Identity failure.
     */
    @Test
    @DisplayName("reports a typed provisioning failure when a new system User has no initial password")
    void shouldReportProvisioningFailureWhenSystemUserPasswordIsMissing() {
        // Arrange
        UserStore users = mock(UserStore.class);
        when(users.findByUsername(SystemUser.USERNAME)).thenReturn(Optional.empty());
        var service = service(users);

        // Act + Assert
        assertThatThrownBy(() -> service.reconcileUser("system", null, null, "System", "User", Set.of(), Set.of()))
            .isInstanceOf(IdentityProvisioningException.class)
            .hasMessageContaining("initial password hash is required");
    }

    private static DefaultIdentityProvisioningService service(UserStore users) {
        return service(users, mock(SubjectGrantService.class), mock(GroupService.class));
    }

    private static DefaultIdentityProvisioningService service(
        UserStore users,
        SubjectGrantService grants,
        GroupService groups
    ) {
        return new DefaultIdentityProvisioningService(users, grants, groups);
    }
}
