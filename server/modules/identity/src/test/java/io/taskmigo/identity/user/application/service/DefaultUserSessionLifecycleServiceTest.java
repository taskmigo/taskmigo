package io.taskmigo.identity.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DefaultUserSessionLifecycleServiceTest {

    private final UserCommandService users = mock(UserCommandService.class);
    private final DefaultUserSessionLifecycleService service = new DefaultUserSessionLifecycleService(
        this.users,
        transactions()
    );

    /// Verifies that active accounts can persist session state after locking their row.
    /// Given: a persisted ACTIVE User and a write callback.
    /// Expect: the callback runs and the operation reports success.
    @Test
    @DisplayName("allows session writes for the locked active User")
    void shouldRunCallbackWhenPersistedUserIsActive() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(this.users.findByIdForUpdate(id)).thenReturn(Optional.of(user(id, UserStatus.ACTIVE)));
        AtomicBoolean called = new AtomicBoolean();
        // Act
        boolean result = this.service.runIfActive(id, () -> called.set(true));
        // Assert
        assertThat(result).isTrue();
        assertThat(called).isTrue();
    }

    /// Verifies that inactive accounts cannot create or recreate authenticated state.
    /// Given: a persisted User in any status other than ACTIVE.
    /// Expect: no callback executes and the operation reports rejection.
    @ParameterizedTest
    @EnumSource(value = UserStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "ACTIVE")
    @DisplayName("rejects session writes for every inactive lifecycle state")
    void shouldRejectCallbackWhenPersistedUserIsInactive(UserStatus status) {
        // Arrange
        UUID id = UUID.randomUUID();
        when(this.users.findByIdForUpdate(id)).thenReturn(Optional.of(user(id, status)));
        AtomicBoolean called = new AtomicBoolean();
        // Act
        boolean result = this.service.runIfActive(id, () -> called.set(true));
        // Assert
        assertThat(result).isFalse();
        assertThat(called).isFalse();
    }

    /// Verifies that an old identity cannot attach writes to a replacement account.
    /// Given: a User UUID which no longer exists.
    /// Expect: the callback does not run even if a different User reuses its username.
    @Test
    @DisplayName("rejects session writes when the stable User id is missing")
    void shouldRejectCallbackWhenUserIsMissing() {
        // Arrange
        UUID id = UUID.randomUUID();
        AtomicBoolean called = new AtomicBoolean();
        // Act
        boolean result = this.service.runIfActive(id, () -> called.set(true));
        // Assert
        assertThat(result).isFalse();
        assertThat(called).isFalse();
    }

    /// Verifies storage errors propagate to the enclosing transaction.
    /// Given: an ACTIVE User whose session write throws.
    /// Expect: the original exception escapes so the transaction can roll back.
    @Test
    @DisplayName("propagates session persistence failures")
    void shouldPropagateFailureWhenCallbackThrows() {
        // Arrange
        UUID id = UUID.randomUUID();
        when(this.users.findByIdForUpdate(id)).thenReturn(Optional.of(user(id, UserStatus.ACTIVE)));
        // Act + Assert
        assertThatThrownBy(() ->
            this.service.runIfActive(id, () -> {
                throw new IllegalStateException("write failed");
            })
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("write failed");
    }

    private static User user(UUID id, UserStatus status) {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        if (status == UserStatus.TOMBSTONE) {
            return User.restoreTombstone(id, null, now);
        }
        return User.restore(
            id,
            "session-user",
            Set.of(),
            "Session",
            "User",
            status,
            status == UserStatus.RETAINED ? now : null,
            null,
            null
        );
    }

    private static TransactionRunner transactions() {
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
