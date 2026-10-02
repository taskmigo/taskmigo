package io.taskmigo.identity.user.application.service;

import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.configuration.RetentionDuration;
import io.taskmigo.identity.configuration.application.port.in.api.ConfigurationService;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.api.UserRetentionService;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;
import java.util.UUID;

/// Purges expired retained Users with per-User database locking for repeated and multi-instance safety.
public final class DefaultUserRetentionService implements UserRetentionService {

    private final UserCommandService users;
    private final ConfigurationService configuration;
    private final TransactionRunner transactions;

    public DefaultUserRetentionService(
        UserCommandService users,
        ConfigurationService configuration,
        TransactionRunner transactions
    ) {
        this.users = users;
        this.configuration = configuration;
        this.transactions = transactions;
    }

    @Override
    public int purgeExpiredUsers(Instant now) {
        RetentionDuration retention = this.configuration.get().retention().user();
        Instant cutoff = now.minus(retention.duration());
        int purged = 0;
        for (UUID userId : this.users.retainedBefore(cutoff)) {
            if (this.transactions.write(() -> this.purgeIfStillEligible(userId, cutoff))) {
                purged++;
            }
        }
        return purged;
    }

    private boolean purgeIfStillEligible(UUID userId, Instant cutoff) {
        User user = this.users.findByIdForUpdate(userId).orElse(null);
        if (
            user == null ||
            user.status() != UserStatus.RETAINED ||
            user.retainedAt() == null ||
            user.retainedAt().isAfter(cutoff)
        ) {
            return false;
        }
        this.users.delete(user);
        return true;
    }
}
