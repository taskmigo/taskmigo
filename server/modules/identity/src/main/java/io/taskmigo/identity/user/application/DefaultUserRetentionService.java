package io.taskmigo.identity.user.application;

import io.taskmigo.identity.configuration.RetentionDuration;
import io.taskmigo.identity.configuration.application.api.ConfigurationService;
import io.taskmigo.identity.user.UserMutationActor;
import io.taskmigo.identity.user.application.api.UserRetentionService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Tombstones expired retained Users with persistent multi-worker row claims and per-User transactions.
public final class DefaultUserRetentionService implements UserRetentionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultUserRetentionService.class);

    private final ConfigurationService configuration;
    private final UserRetentionBatchService batches;

    public DefaultUserRetentionService(ConfigurationService configuration, UserRetentionBatchService batches) {
        this.configuration = configuration;
        this.batches = batches;
    }

    @Override
    public int purgeExpiredUsers(Instant now) {
        RetentionDuration retention = this.configuration.get().retention().user();
        Instant cutoff = now.minus(retention.duration());
        UserMutationActor actor = this.batches.systemActor();
        int tombstoned = 0;
        RetainedUserCandidate cursor = null;

        while (true) {
            List<RetainedUserCandidate> candidates = this.batches.retainedCandidates(cutoff, cursor);
            if (candidates.isEmpty()) {
                return tombstoned;
            }

            for (RetainedUserCandidate candidate : candidates) {
                cursor = candidate;
                try {
                    if (this.batches.tombstoneIfStillEligible(candidate, cutoff, actor, now)) {
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
}
