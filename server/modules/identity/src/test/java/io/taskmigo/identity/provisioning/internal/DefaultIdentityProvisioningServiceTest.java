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
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

class DefaultIdentityProvisioningServiceTest {

    /**
     * Verifies that reconciling a missing managed User creates it and reports the creation action.
     * Given: a valid User with an initial encoded password and no persisted User with that username.
     * Expect: the created identifier is returned with the `ADDED` action.
     */
    @Test
    @DisplayName("reports an added action when a managed User is missing")
    void shouldReportAddedActionWhenManagedUserIsMissing() {
        // Arrange
        UserStore users = mock(UserStore.class);
        when(users.findByUsername("alice")).thenReturn(Optional.empty());
        var service = service(users);

        // Act
        ReconciliationResult<UUID> result = service.reconcileUser(
            "alice",
            "{noop}password",
            List.of(),
            "Alice",
            "User",
            Set.of(),
            Set.of()
        );

        // Assert
        assertThat(result.action()).isEqualTo(ReconciliationAction.ADDED);
        verify(users).create(ArgumentMatchers.any(UserState.class));
    }

    /**
     * Verifies that reconciling an existing managed User reports an update and retains its identifier.
     * Given: an existing User with a changed profile and no replacement password.
     * Expect: profile and grants are reconciled and the result has the `UPDATED` action.
     */
    @Test
    @DisplayName("reports an updated action when a managed User exists")
    void shouldReportUpdatedActionWhenManagedUserExists() {
        // Arrange
        UUID id = UUID.randomUUID();
        UserStore users = mock(UserStore.class);
        when(users.findByUsername("alice")).thenReturn(
            Optional.of(new UserState(id, "alice", Set.of(), "Old", "User", true, "{noop}old"))
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
    }

    /**
     * Verifies that reconciling an identical managed User reports no change and skips persistence.
     * Given: a persisted User whose profile, password hash, Role grants, Statement grants, and Groups match.
     * Expect: the existing identifier is returned with `UNCHANGED`, with no User or grant update.
     */
    @Test
    @DisplayName("reports unchanged when a managed User data is identical")
    void shouldReportUnchangedWhenManagedUserDataIsIdentical() {
        // Arrange
        UUID id = UUID.randomUUID();
        UserStore users = mock(UserStore.class);
        when(users.findByUsername("alice")).thenReturn(
            Optional.of(
                new UserState(id, "alice", Set.of("alice@example.com"), "Alice", "User", true, "{noop}password")
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
            "{noop}password",
            List.of("alice@example.com"),
            "Alice",
            "User",
            Set.of(),
            Set.of()
        );

        // Assert
        assertThat(result).isEqualTo(new ReconciliationResult<>(id, ReconciliationAction.UNCHANGED));
        verify(users, Mockito.never()).updateProfile(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        verify(users, Mockito.never()).updatePasswordHash(Mockito.any(), Mockito.any());
        verify(grants, Mockito.never()).setRoles(Mockito.any(), Mockito.any());
        verify(groups, Mockito.never()).setGroupsForUser(Mockito.any(), Mockito.any());
    }

    /**
     * Verifies that deleting an existing managed User reports a removal.
     * Given: a non-system User found by username.
     * Expect: grants and group memberships are cleared, the User is deleted, and the operation returns `true`.
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
     * Given: a username that is absent from persistence.
     * Expect: deletion returns `false` and no User delete operation is attempted.
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
     * Verifies: a new system user cannot be provisioned without a pre-encoded password.
     * Given: no existing system user and a null password hash.
     * Expect: the provisioning boundary raises a typed identity failure.
     */
    @Test
    @DisplayName("reports a typed provisioning failure when a new system User has no initial password")
    void shouldReportProvisioningFailureWhenSystemUserPasswordIsMissing() {
        UserStore users = mock(UserStore.class);
        when(users.findByUsername(SystemUser.USERNAME)).thenReturn(Optional.empty());
        var service = new DefaultIdentityProvisioningService(
            users,
            mock(SubjectGrantService.class),
            mock(GroupService.class)
        );

        assertThatThrownBy(() -> service.reconcileUser("system", null, null, "System", "User", Set.of(), Set.of()))
            .isInstanceOf(IdentityProvisioningException.class)
            .hasMessageContaining("password hash is required");
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
