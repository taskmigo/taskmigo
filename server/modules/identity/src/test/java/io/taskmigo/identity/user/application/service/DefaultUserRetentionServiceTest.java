package io.taskmigo.identity.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.configuration.ConfigurationSnapshot;
import io.taskmigo.identity.configuration.ConfigurationSnapshot.RetentionConfiguration;
import io.taskmigo.identity.configuration.RetentionDuration;
import io.taskmigo.identity.configuration.application.port.in.api.ConfigurationService;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.in.internal.UserTombstoneService;
import io.taskmigo.identity.user.application.port.out.RetainedUserCandidate;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultUserRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    /**
     * Given: an expired retained User discovered by the current retention cutoff.
     * Expect: the service claims that User and delegates one tombstone operation using the system actor.
     */
    @Test
    @DisplayName("tombstones retained users after the current retention deadline")
    void shouldTombstoneRetainedUserWhenCurrentDeadlineExpired() {
        // Arrange
        Instant retainedAt = Instant.parse("2026-08-01T00:00:00Z");
        Instant cutoff = NOW.minus(RetentionDuration.parse("P30D").duration());
        UUID userId = UUID.randomUUID();
        User user = retainedUser(userId, retainedAt);
        RetainedUserCandidate candidate = new RetainedUserCandidate(userId, retainedAt);
        User system = systemUser();
        UserCommandService users = mock(UserCommandService.class);
        ConfigurationService configuration = mock(ConfigurationService.class);
        UserTombstoneService tombstones = mock(UserTombstoneService.class);
        when(configuration.get()).thenReturn(configuration("P30D"));
        when(users.findByUsername(SystemUser.USERNAME)).thenReturn(Optional.of(system));
        when(users.retainedCandidates(cutoff, null)).thenReturn(List.of(candidate));
        when(users.claimRetainedForUpdate(userId)).thenReturn(Optional.of(user));
        var service = new DefaultUserRetentionService(users, configuration, tombstones, directTransactions());

        // Act
        int tombstoned = service.purgeExpiredUsers(NOW);

        // Assert
        assertThat(tombstoned).isEqualTo(1);
        verify(tombstones).tombstone(user, new UserMutationActor(system.id(), SystemUser.USERNAME), NOW);
    }

    /**
     * Given: discovery sees a retained User but another Worker owns or changed the row before the claim.
     * Expect: the service skips the candidate without calling the tombstone operation.
     */
    @Test
    @DisplayName("skips candidates that cannot be claimed")
    void shouldSkipCandidateWhenClaimIsUnavailable() {
        // Arrange
        Instant retainedAt = Instant.parse("2026-08-01T00:00:00Z");
        Instant cutoff = NOW.minus(RetentionDuration.parse("P30D").duration());
        UUID userId = UUID.randomUUID();
        RetainedUserCandidate candidate = new RetainedUserCandidate(userId, retainedAt);
        UserCommandService users = mock(UserCommandService.class);
        ConfigurationService configuration = mock(ConfigurationService.class);
        UserTombstoneService tombstones = mock(UserTombstoneService.class);
        when(configuration.get()).thenReturn(configuration("P30D"));
        when(users.findByUsername(SystemUser.USERNAME)).thenReturn(Optional.of(systemUser()));
        when(users.retainedCandidates(cutoff, null)).thenReturn(List.of(candidate));
        when(users.claimRetainedForUpdate(userId)).thenReturn(Optional.empty());
        var service = new DefaultUserRetentionService(users, configuration, tombstones, directTransactions());

        // Act
        int tombstoned = service.purgeExpiredUsers(NOW);

        // Assert
        assertThat(tombstoned).isZero();
        verify(tombstones, never()).tombstone(any(), any(), any());
    }

    /**
     * Given: the first claimed User fails during tombstoning and a later candidate is healthy.
     * Expect: the failed User rolls back through the transaction boundary and later candidates still run.
     */
    @Test
    @DisplayName("continues after one retained user tombstone fails")
    void shouldContinueWhenOneTombstoneFails() {
        // Arrange
        Instant cutoff = NOW.minus(RetentionDuration.parse("P30D").duration());
        RetainedUserCandidate first = new RetainedUserCandidate(
            UUID.randomUUID(),
            Instant.parse("2026-07-01T00:00:00Z")
        );
        RetainedUserCandidate second = new RetainedUserCandidate(
            UUID.randomUUID(),
            Instant.parse("2026-08-01T00:00:00Z")
        );
        User firstUser = retainedUser(first.id(), first.retainedAt());
        User secondUser = retainedUser(second.id(), second.retainedAt());
        UserCommandService users = mock(UserCommandService.class);
        ConfigurationService configuration = mock(ConfigurationService.class);
        UserTombstoneService tombstones = mock(UserTombstoneService.class);
        when(configuration.get()).thenReturn(configuration("P30D"));
        when(users.findByUsername(SystemUser.USERNAME)).thenReturn(Optional.of(systemUser()));
        when(users.retainedCandidates(cutoff, null)).thenReturn(List.of(first, second));
        when(users.claimRetainedForUpdate(first.id())).thenReturn(Optional.of(firstUser));
        when(users.claimRetainedForUpdate(second.id())).thenReturn(Optional.of(secondUser));
        doThrow(new IllegalStateException("simulated failure"))
            .when(tombstones)
            .tombstone(eq(firstUser), any(), eq(NOW));
        var service = new DefaultUserRetentionService(users, configuration, tombstones, directTransactions());

        // Act
        int tombstoned = service.purgeExpiredUsers(NOW);

        // Assert
        assertThat(tombstoned).isEqualTo(1);
        verify(tombstones).tombstone(eq(secondUser), any(), eq(NOW));
    }

    private static ConfigurationSnapshot configuration(String retention) {
        return new ConfigurationSnapshot(new RetentionConfiguration(RetentionDuration.parse(retention)));
    }

    private static User retainedUser(UUID id, Instant retainedAt) {
        return User.restore(
            id,
            "retained-user-" + id,
            Set.of(),
            "Retained",
            "User",
            UserStatus.RETAINED,
            retainedAt,
            null,
            null
        );
    }

    private static User systemUser() {
        return User.restore(
            UUID.randomUUID(),
            SystemUser.USERNAME,
            Set.of(),
            "System",
            "User",
            UserStatus.ACTIVE,
            "{bcrypt}hash"
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
