package io.taskmigo.identity.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.internal.UserCommandService;
import io.taskmigo.identity.user.application.internal.UserTombstoneService;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class UserRetentionBatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T00:00:00Z");

    /**
     * Verifies that candidate discovery stays in the dedicated read transaction boundary.
     *
     * Given: a cutoff and keyset cursor with one candidate returned by the command service.
     * Expect: the batch collaborator returns that candidate unchanged.
     */
    @Test
    @DisplayName("delegates retained candidate discovery")
    void shouldReturnCandidatesWhenRetainedUsersAreDiscovered() {
        // Arrange
        UserCommandService users = mock(UserCommandService.class);
        RetainedUserCandidate after = new RetainedUserCandidate(UUID.randomUUID(), NOW.minusSeconds(3600));
        RetainedUserCandidate candidate = new RetainedUserCandidate(UUID.randomUUID(), NOW.minusSeconds(7200));
        when(users.retainedCandidates(NOW, after)).thenReturn(List.of(candidate));
        var service = new UserRetentionBatchService(users, mock(UserTombstoneService.class));

        // Act
        List<RetainedUserCandidate> result = service.retainedCandidates(NOW, after);

        // Assert
        assertThat(result).containsExactly(candidate);
    }

    /**
     * Verifies that one claimed retained User is tombstoned only when it remains eligible at claim time.
     *
     * Given: a claimed RETAINED User whose retained timestamp is at or before the cutoff.
     * Expect: the tombstone service runs once and the batch operation returns true.
     */
    @Test
    @DisplayName("tombstones a claimed retained user that remains eligible")
    void shouldTombstoneUserWhenClaimedUserRemainsEligible() {
        // Arrange
        Instant cutoff = NOW.minusSeconds(3600);
        RetainedUserCandidate candidate = new RetainedUserCandidate(UUID.randomUUID(), cutoff.minusSeconds(1));
        User user = retainedUser(candidate.id(), candidate.retainedAt());
        UserCommandService users = mock(UserCommandService.class);
        UserTombstoneService tombstones = mock(UserTombstoneService.class);
        when(users.claimRetainedForUpdate(candidate.id())).thenReturn(Optional.of(user));
        var service = new UserRetentionBatchService(users, tombstones);

        // Act
        boolean result = service.tombstoneIfStillEligible(candidate, cutoff, NOW);

        // Assert
        assertThat(result).isTrue();
        verify(tombstones).tombstone(user, NOW);
    }

    /**
     * Verifies that a candidate that cannot be claimed is ignored inside its independent write boundary.
     *
     * Given: candidate discovery succeeded but the claim returns empty.
     * Expect: no tombstone mutation occurs and the batch operation returns false.
     */
    @Test
    @DisplayName("skips a retained candidate that cannot be claimed")
    void shouldSkipCandidateWhenClaimIsUnavailable() {
        // Arrange
        RetainedUserCandidate candidate = new RetainedUserCandidate(UUID.randomUUID(), NOW.minusSeconds(7200));
        UserCommandService users = mock(UserCommandService.class);
        UserTombstoneService tombstones = mock(UserTombstoneService.class);
        when(users.claimRetainedForUpdate(candidate.id())).thenReturn(Optional.empty());
        var service = new UserRetentionBatchService(users, tombstones);

        // Act
        boolean result = service.tombstoneIfStillEligible(candidate, NOW.minusSeconds(3600), NOW);

        // Assert
        assertThat(result).isFalse();
        verify(tombstones, never()).tombstone(ArgumentMatchers.any(), ArgumentMatchers.any());
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
}
