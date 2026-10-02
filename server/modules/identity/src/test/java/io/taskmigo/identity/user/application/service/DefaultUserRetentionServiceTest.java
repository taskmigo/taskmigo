package io.taskmigo.identity.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.configuration.ConfigurationSnapshot;
import io.taskmigo.identity.configuration.ConfigurationSnapshot.RetentionConfiguration;
import io.taskmigo.identity.configuration.RetentionDuration;
import io.taskmigo.identity.configuration.application.port.in.api.ConfigurationService;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class DefaultUserRetentionServiceTest {

    /**
     * Verifies purge eligibility is recalculated from the current retention configuration.
     *
     * Given: a retained User older than the current 30-day cutoff.
     * Expect: the service re-locks and physically deletes the still-retained User.
     */
    @Test
    @DisplayName("purges retained users after the current retention deadline")
    void shouldPurgeRetainedUserWhenCurrentDeadlineExpired() {
        // Arrange
        Instant now = Instant.parse("2026-10-02T00:00:00Z");
        Instant retainedAt = Instant.parse("2026-08-01T00:00:00Z");
        UUID userId = UUID.randomUUID();
        User user = retainedUser(userId, retainedAt);
        UserCommandService users = mock(UserCommandService.class);
        ConfigurationService configuration = mock(ConfigurationService.class);
        when(configuration.get()).thenReturn(configuration("P30D"));
        when(users.retainedBefore(now.minus(RetentionDuration.parse("P30D").duration()))).thenReturn(List.of(userId));
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        var service = new DefaultUserRetentionService(users, configuration, directTransactions());

        // Act
        int purged = service.purgeExpiredUsers(now);

        // Assert
        assertThat(purged).isEqualTo(1);
        verify(users).delete(user);
    }

    /**
     * Verifies a stale candidate cannot be purged after another executor changes or removes it.
     *
     * Given: discovery returns a retained User id but the locked row is already absent.
     * Expect: the purge is idempotent and performs no delete.
     */
    @Test
    @DisplayName("skips candidates that disappear before the purge lock")
    void shouldSkipCandidateWhenUserIsMissingAtLockTime() {
        // Arrange
        Instant now = Instant.parse("2026-10-02T00:00:00Z");
        UUID userId = UUID.randomUUID();
        UserCommandService users = mock(UserCommandService.class);
        ConfigurationService configuration = mock(ConfigurationService.class);
        when(configuration.get()).thenReturn(configuration("P30D"));
        when(users.retainedBefore(now.minus(RetentionDuration.parse("P30D").duration()))).thenReturn(List.of(userId));
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.empty());
        var service = new DefaultUserRetentionService(users, configuration, directTransactions());

        // Act
        int purged = service.purgeExpiredUsers(now);

        // Assert
        assertThat(purged).isZero();
        verify(users, never()).delete(ArgumentMatchers.any());
    }

    private static ConfigurationSnapshot configuration(String retention) {
        return new ConfigurationSnapshot(new RetentionConfiguration(RetentionDuration.parse(retention)));
    }

    private static User retainedUser(UUID id, Instant retainedAt) {
        return User.restore(id, "retained-user", Set.of(), "Retained", "User", UserStatus.RETAINED, retainedAt, null);
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
