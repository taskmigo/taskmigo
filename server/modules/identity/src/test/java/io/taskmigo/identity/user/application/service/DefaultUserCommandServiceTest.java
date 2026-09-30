package io.taskmigo.identity.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.identity.user.application.port.in.internal.UserMutationResult;
import io.taskmigo.identity.user.application.port.out.UserCommandRepository;
import io.taskmigo.identity.user.domain.User;
import io.taskmigo.identity.user.domain.UserStatus;
import io.taskmigo.identity.user.domain.Username;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultUserCommandServiceTest {

    /**
     * Verifies that a missing managed User is created through the canonical aggregate.
     *
     * Given: no persisted User for the normalized username.
     * Expect: one normalized aggregate is saved and creation carries no update audit diff.
     */
    @Test
    @DisplayName("creates a missing managed user through the aggregate")
    void shouldCreateManagedUserWhenUsernameIsMissing() {
        // Arrange
        UserCommandRepository users = mock(UserCommandRepository.class);
        when(users.findByUsername(Username.of("alice"))).thenReturn(Optional.empty());
        var service = new DefaultUserCommandService(users);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);

        // Act
        UserMutationResult result = service.reconcileManaged(
            "  alice ",
            "{bcrypt}initial",
            List.of("Alice@EXAMPLE.COM"),
            " Alice ",
            " User "
        );

        // Assert
        assertThat(result.created()).isTrue();
        assertThat(result.changed()).isTrue();
        assertThat(result.changes()).isEmpty();
        verify(users).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(result.id());
        assertThat(saved.getValue().username().value()).isEqualTo("alice");
        assertThat(saved.getValue().profile().emails()).containsExactly("alice@example.com");
        assertThat(saved.getValue().credential().passwordHash()).isEqualTo("{bcrypt}initial");
    }

    /**
     * Verifies that re-running managed input cannot rotate an initialized User credential.
     *
     * Given: an existing User whose profile already matches and whose credential is initialized.
     * Expect: reconciliation is unchanged, has no audit diff, and does not save the aggregate.
     */
    @Test
    @DisplayName("does not save when only a different initial password is supplied")
    void shouldNotChangeManagedUserWhenCredentialAlreadyExists() {
        // Arrange
        UserCommandRepository users = mock(UserCommandRepository.class);
        User existing = User.restore(
            UUID.randomUUID(),
            "alice",
            Set.of("alice@example.com"),
            "Alice",
            "User",
            UserStatus.ACTIVE,
            "{bcrypt}existing"
        );
        when(users.findByUsername(Username.of("alice"))).thenReturn(Optional.of(existing));
        var service = new DefaultUserCommandService(users);

        // Act
        UserMutationResult result = service.reconcileManaged(
            "alice",
            "{bcrypt}different",
            List.of("alice@example.com"),
            "Alice",
            "User"
        );

        // Assert
        assertThat(result).isEqualTo(new UserMutationResult(existing.id(), false, false));
        assertThat(existing.credential().passwordHash()).isEqualTo("{bcrypt}existing");
        verify(users, never()).save(existing);
    }

    /**
     * Verifies that a missing credential becomes a sensitive audit marker instead of exposing the hash.
     *
     * Given: an existing User with no password hash.
     * Expect: reconciliation initializes the hash and reports only a sensitive passwordHash change marker.
     */
    @Test
    @DisplayName("marks managed credential initialization as sensitive")
    void shouldMarkCredentialChangeSensitiveWhenManagedUserHasNoPassword() {
        // Arrange
        UserCommandRepository users = mock(UserCommandRepository.class);
        User existing = User.restore(UUID.randomUUID(), "alice", Set.of(), "Alice", "User", UserStatus.ACTIVE, null);
        when(users.findByUsername(Username.of("alice"))).thenReturn(Optional.of(existing));
        var service = new DefaultUserCommandService(users);

        // Act
        UserMutationResult result = service.reconcileManaged("alice", "{bcrypt}initial", Set.of(), "Alice", "User");

        // Assert
        assertThat(result.created()).isFalse();
        assertThat(result.changed()).isTrue();
        assertThat(result.changes())
            .singleElement()
            .satisfies(change -> {
                assertThat(change.field()).isEqualTo("passwordHash");
                assertThat(change.sensitive()).isTrue();
                assertThat(change.before()).isNull();
                assertThat(change.after()).isNull();
            });
        assertThat(existing.credential().passwordHash()).isEqualTo("{bcrypt}initial");
        verify(users).save(existing);
    }

    /**
     * Verifies that managed profile reconciliation reports deterministic field-level audit changes.
     *
     * Given: an existing User whose emails and names differ from managed desired state.
     * Expect: the aggregate is saved and the result contains only the changed canonical fields.
     */
    @Test
    @DisplayName("captures managed profile field changes")
    void shouldCaptureAuditChangesWhenManagedProfileChanges() {
        // Arrange
        UserCommandRepository users = mock(UserCommandRepository.class);
        User existing = User.restore(
            UUID.randomUUID(),
            "alice",
            Set.of("old@example.com"),
            "Old",
            "Name",
            UserStatus.ACTIVE,
            null
        );
        when(users.findByUsername(Username.of("alice"))).thenReturn(Optional.of(existing));
        var service = new DefaultUserCommandService(users);

        // Act
        UserMutationResult result = service.reconcileManaged("alice", null, List.of("NEW@example.com"), "New", "User");

        // Assert
        assertThat(result.changed()).isTrue();
        assertThat(result.changes())
            .extracting(change -> change.field())
            .containsExactly("emails", "firstName", "lastName");
        assertThat(result.changes().get(0).before()).isEqualTo(List.of("old@example.com"));
        assertThat(result.changes().get(0).after()).isEqualTo(List.of("new@example.com"));
        verify(users).save(existing);
    }
}
