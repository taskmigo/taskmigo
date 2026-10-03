package io.taskmigo.identity.user.application.service;

import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.configuration.RetentionDuration;
import io.taskmigo.identity.configuration.application.port.in.api.ConfigurationService;
import io.taskmigo.identity.user.SystemUser;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.api.UserRetentionService;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.application.port.out.RetainedUserCandidate;
import io.taskmigo.identity.user.application.port.in.internal.UserTombstoneService;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Tombstones expired retained Users with persistent multi-worker row claims and per-User transactions.
public final class DefaultUserRetentionService implements UserRetentionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUserRetentionService.class);

    private final UserCommandService users;
    private final ConfigurationService configuration;
    private final UserTombstoneService tombstones;
    private final TransactionRunner transactions;

    public DefaultUserRetentionService(
        UserCommandService users,
        ConfigurationService configuration,
        UserTombstoneService tombstones,
        TransactionRunner transactions
    ) {
        this.users = users;
        this.configuration = configuration;
        this.tombstones = tombstones;
        this.transactions = transactions;
    }

    @Override
    public int purgeExpiredUsers(Instant now) {
        RetentionDuration retention = this.configuration.get().retention().user();
        Instant cutoff = now.minus(retention.duration());
        UserMutationActor actor = this.transactions.read(this::systemActor);
        int tombstoned = 0;
        RetainedUserCandidate cursor = null;

        while (true) {
            RetainedUserCandidate after = cursor;
            List<RetainedUserCandidate> candidates = this.transactions.read(() ->
                this.users.retainedCandidates(cutoff, after)
            );
            if (candidates.isEmpty()) {
                return tombstoned;
            }

            for (RetainedUserCandidate candidate : candidates) {
                cursor = candidate;
                try {
                    if (this.transactions.write(() -> this.tombstoneIfStillEligible(candidate, cutoff, actor, now))) {
                        tombstoned++;
                    }
                } catch (RuntimeException exception) {
                    LOGGER.warn("Failed to tombstone retained User {}", candidate.id(), exception);
                }
            }

            if (candidates.size() < 100) {
                return tombstoned;
            }
        }
    }

    private UserMutationActor systemActor() {
        User system = this.users
            .findByUsername(SystemUser.USERNAME)
            .orElseThrow(() -> new IllegalStateException("System User must exist before retention purge runs"));
        return new UserMutationActor(system.id(), system.username().value());
    }

    private boolean tombstoneIfStillEligible(
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
