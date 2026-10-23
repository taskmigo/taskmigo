package io.taskmigo.identity.user.application;

import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.internal.UserCommandService;
import io.taskmigo.identity.user.application.internal.UserTombstoneService;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

/// Executes the independent transaction scopes used by retention batch processing.
public class UserRetentionBatchService {

    private final UserCommandService users;
    private final UserTombstoneService tombstones;

    public UserRetentionBatchService(UserCommandService users, UserTombstoneService tombstones) {
        this.users = users;
        this.tombstones = tombstones;
    }

    @Transactional(readOnly = true)
    public UserMutationActor systemActor() {
        User system = this.users
            .findByUsername(SystemUser.USERNAME)
            .orElseThrow(() -> new IllegalStateException("System User must exist before retention purge runs"));
        return new UserMutationActor(system.id(), system.username().value());
    }

    @Transactional(readOnly = true)
    public List<RetainedUserCandidate> retainedCandidates(Instant cutoff, @Nullable RetainedUserCandidate after) {
        return this.users.retainedCandidates(cutoff, after);
    }

    @Transactional
    public boolean tombstoneIfStillEligible(
        RetainedUserCandidate candidate,
        Instant cutoff,
        UserMutationActor actor,
        Instant tombstonedAt
    ) {
        User user = this.users.claimRetainedForUpdate(candidate.id()).orElse(null);
        if (
            user == null ||
            user.status() != UserStatus.RETAINED ||
            user.retainedAt() == null ||
            user.retainedAt().isAfter(cutoff)
        ) {
            return false;
        }
        this.tombstones.tombstone(user, actor, tombstonedAt);
        return true;
    }
}
