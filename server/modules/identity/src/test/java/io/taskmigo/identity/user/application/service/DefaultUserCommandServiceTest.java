package io.taskmigo.identity.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.audit.AuditEvent;
import io.taskmigo.audit.AuditPublisher;
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
     * Verifies a missing managed User is created through the canonical aggregate.
     *
     * Given: no persisted User for the normalized username.
     * Expect: one normalized aggregate is saved and the mutation reports creation.
     */
    @Test
    @DisplayName("creates a missing managed user through the aggregate")
    void shouldCreateManagedUserWhenUsernameIsMissing() {
        // Arrange
        UserCommandRepository users = mock(UserCommandRepository.class);
        when(users.findByUsername(Username.of("alice"))).thenReturn(Optional.empty());
        var service = new DefaultUserCommandService(users, event -> {});
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
        verify(users).save(saved.capture());
        assertThat(saved.getValue().id()).isEqualTo(result.id());
        assertThat(saved.getValue().username().value()).isEqualTo("alice");
        assertThat(saved.getValue().profile().emails()).containsExactly("alice@example.com");
        assertThat(saved.getValue().credential().passwordHash()).isEqualTo("{bcrypt}initial");
    }

    /**
     * Verifies re-running managed input cannot rotate an initialized User credential.
     *
     * Given: an existing User whose profile already matches and whose credential is initialized.
     * Expect: reconciliation is unchanged and no aggregate save occurs.
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
        AuditPublisher audit = mock(AuditPublisher.class);
        var service = new DefaultUserCommandService(users, audit);

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
        verify(audit, never()).publish(org.mockito.ArgumentMatchers.any());
    }

    /**
     * Verifies a missing credential may be initialized once for an existing managed User.
     *
     * Given: an existing User with no password hash.
     * Expect: reconciliation initializes the hash, saves the aggregate, and reports a change.
     */
    @Test
    @DisplayName("initializes a missing managed credential")
    void shouldInitializeCredentialWhenManagedUserHasNoPassword() {
        // Arrange
        UserCommandRepository users = mock(UserCommandRepository.class);
        User existing = User.restore(UUID.randomUUID(), "alice", Set.of(), "Alice", "User", UserStatus.ACTIVE, null);
        when(users.findByUsername(Username.of("alice"))).thenReturn(Optional.of(existing));
        AuditPublisher audit = mock(AuditPublisher.class);
        var service = new DefaultUserCommandService(users, audit);
        ArgumentCaptor<AuditEvent> published = ArgumentCaptor.forClass(AuditEvent.class);

        // Act
        UserMutationResult result = service.reconcileManaged("alice", "{bcrypt}initial", Set.of(), "Alice", "User");

        // Assert
        assertThat(result).isEqualTo(new UserMutationResult(existing.id(), false, true));
        assertThat(existing.credential().passwordHash()).isEqualTo("{bcrypt}initial");
        verify(users).save(existing);
        verify(audit).publish(published.capture());
        assertThat(published.getValue().entityType()).isEqualTo("user");
        assertThat(published.getValue().entityId()).isEqualTo(existing.id());
        assertThat(published.getValue().changes())
            .singleElement()
            .satisfies(change -> {
                assertThat(change.field()).isEqualTo("password");
                assertThat(change.sensitive()).isTrue();
                assertThat(change.beforeValue()).isNull();
                assertThat(change.afterValue()).isNull();
            });
    }

    @Test
    @DisplayName("publishes one field-level audit event for profile changes")
    void shouldPublishAuditDiffForManagedProfileUpdate() {
        // Arrange
        UserCommandRepository users = mock(UserCommandRepository.class);
        User existing = User.restore(
            UUID.randomUUID(),
            "alice",
            Set.of("old@example.com"),
            "Alice",
            "Old",
            UserStatus.ACTIVE,
            "{bcrypt}existing"
        );
        when(users.findByUsername(Username.of("alice"))).thenReturn(Optional.of(existing));
        AuditPublisher audit = mock(AuditPublisher.class);
        var service = new DefaultUserCommandService(users, audit);
        ArgumentCaptor<AuditEvent> published = ArgumentCaptor.forClass(AuditEvent.class);

        // Act
        UserMutationResult result = service.reconcileManaged("alice", null, Set.of("new@example.com"), "Alice", "New");

        // Assert
        assertThat(result.changed()).isTrue();
        verify(users).save(existing);
        verify(audit).publish(published.capture());
        AuditEvent event = published.getValue();
        assertThat(event.entityType()).isEqualTo("user");
        assertThat(event.entityId()).isEqualTo(existing.id());
        assertThat(event.changes())
            .extracting(change -> change.field())
            .containsExactly("emails", "lastName");
        assertThat(event.changes().get(0).beforeValue()).isEqualTo(Set.of("old@example.com"));
        assertThat(event.changes().get(0).afterValue()).isEqualTo(Set.of("new@example.com"));
        assertThat(event.changes().get(1).beforeValue()).isEqualTo("Old");
        assertThat(event.changes().get(1).afterValue()).isEqualTo("New");
    }
}
