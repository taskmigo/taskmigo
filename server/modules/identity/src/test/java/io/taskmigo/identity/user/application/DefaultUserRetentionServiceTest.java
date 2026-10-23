package io.taskmigo.identity.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.identity.configuration.ConfigurationSnapshot;
import io.taskmigo.identity.configuration.ConfigurationSnapshot.RetentionConfiguration;
import io.taskmigo.identity.configuration.RetentionDuration;
import io.taskmigo.identity.configuration.application.api.ConfigurationService;
import io.taskmigo.identity.user.UserMutationActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultUserRetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    /**
     * Verifies that the retention coordinator counts a successfully tombstoned expired User.
     *
     * Given: one candidate returned by the transaction-scoped batch collaborator for the current retention cutoff.
     * Expect: that candidate is processed once and the coordinator returns one tombstone.
     */
    @Test
    @DisplayName("counts a retained user after the current retention deadline")
    void shouldCountTombstonedUserWhenCurrentDeadlineExpired() {
        // Arrange
        Instant cutoff = NOW.minus(RetentionDuration.parse("P30D").duration());
        RetainedUserCandidate candidate = new RetainedUserCandidate(
            UUID.randomUUID(),
            Instant.parse("2026-08-01T00:00:00Z")
        );
        ConfigurationService configuration = mock(ConfigurationService.class);
        UserRetentionBatchService batches = mock(UserRetentionBatchService.class);
        UserMutationActor actor = new UserMutationActor(UUID.randomUUID(), "system");
        when(configuration.get()).thenReturn(configuration("P30D"));
        when(batches.systemActor()).thenReturn(actor);
        when(batches.retainedCandidates(cutoff, null)).thenReturn(List.of(candidate));
        when(batches.tombstoneIfStillEligible(candidate, cutoff, actor, NOW)).thenReturn(true);
        var service = new DefaultUserRetentionService(configuration, batches);

        // Act
        int tombstoned = service.purgeExpiredUsers(NOW);

        // Assert
        assertThat(tombstoned).isOne();
        verify(batches).tombstoneIfStillEligible(candidate, cutoff, actor, NOW);
    }

    /**
     * Verifies that a candidate that becomes ineligible after discovery is not counted.
     *
     * Given: one discovered candidate whose transaction-scoped eligibility check returns false.
     * Expect: the coordinator completes successfully and reports zero tombstones.
     */
    @Test
    @DisplayName("skips candidates that are no longer eligible")
    void shouldSkipCandidateWhenEligibilityCheckReturnsFalse() {
        // Arrange
        Instant cutoff = NOW.minus(RetentionDuration.parse("P30D").duration());
        RetainedUserCandidate candidate = new RetainedUserCandidate(
            UUID.randomUUID(),
            Instant.parse("2026-08-01T00:00:00Z")
        );
        ConfigurationService configuration = mock(ConfigurationService.class);
        UserRetentionBatchService batches = mock(UserRetentionBatchService.class);
        UserMutationActor actor = new UserMutationActor(UUID.randomUUID(), "system");
        when(configuration.get()).thenReturn(configuration("P30D"));
        when(batches.systemActor()).thenReturn(actor);
        when(batches.retainedCandidates(cutoff, null)).thenReturn(List.of(candidate));
        when(batches.tombstoneIfStillEligible(candidate, cutoff, actor, NOW)).thenReturn(false);
        var service = new DefaultUserRetentionService(configuration, batches);

        // Act
        int tombstoned = service.purgeExpiredUsers(NOW);

        // Assert
        assertThat(tombstoned).isZero();
    }

    /**
     * Verifies that one per-User transaction failure does not stop later retained Users from being processed.
     *
     * Given: two candidates where the first transaction-scoped tombstone call fails and the second succeeds.
     * Expect: the coordinator catches the first failure, processes the second candidate, and returns one tombstone.
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
        ConfigurationService configuration = mock(ConfigurationService.class);
        UserRetentionBatchService batches = mock(UserRetentionBatchService.class);
        UserMutationActor actor = new UserMutationActor(UUID.randomUUID(), "system");
        when(configuration.get()).thenReturn(configuration("P30D"));
        when(batches.systemActor()).thenReturn(actor);
        when(batches.retainedCandidates(cutoff, null)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("simulated failure"))
            .when(batches)
            .tombstoneIfStillEligible(eq(first), eq(cutoff), eq(actor), eq(NOW));
        when(batches.tombstoneIfStillEligible(second, cutoff, actor, NOW)).thenReturn(true);
        var service = new DefaultUserRetentionService(configuration, batches);

        // Act
        int tombstoned = service.purgeExpiredUsers(NOW);

        // Assert
        assertThat(tombstoned).isOne();
        verify(batches).tombstoneIfStillEligible(second, cutoff, actor, NOW);
    }

    private static ConfigurationSnapshot configuration(String retention) {
        return new ConfigurationSnapshot(new RetentionConfiguration(RetentionDuration.parse(retention)));
    }
}
