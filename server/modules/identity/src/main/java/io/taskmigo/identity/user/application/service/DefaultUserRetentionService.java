package io.taskmigo.identity.user.application.service;

import io.taskmigo.authorization.subject.SubjectRef;
import io.taskmigo.authorization.subject.application.port.in.api.SubjectGrantAssignmentService;
import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.authorization.IdentitySubjects;
import io.taskmigo.identity.membership.application.port.in.api.MembershipService;
import io.taskmigo.identity.configuration.RetentionDuration;
import io.taskmigo.identity.configuration.application.port.in.api.ConfigurationService;
import io.taskmigo.identity.user.UserStatus;
import io.taskmigo.identity.user.application.port.in.api.UserRetentionService;
import io.taskmigo.identity.user.application.port.in.internal.UserCommandService;
import io.taskmigo.identity.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// Purges expired retained Users with per-User database locking for repeated and multi-instance safety.
public final class DefaultUserRetentionService implements UserRetentionService {

    private final UserCommandService users;
    private final ConfigurationService configuration;
    private final SubjectGrantAssignmentService grantAssignments;
    private final MembershipService memberships;
    private final TransactionRunner transactions;

    public DefaultUserRetentionService(
        UserCommandService users,
        ConfigurationService configuration,
        SubjectGrantAssignmentService grantAssignments,
        MembershipService memberships,
        TransactionRunner transactions
    ) {
        this.users = users;
        this.configuration = configuration;
        this.grantAssignments = grantAssignments;
        this.memberships = memberships;
        this.transactions = transactions;
    }

    @Override
    public int purgeExpiredUsers(Instant now) {
        RetentionDuration retention = this.configuration.get().retention().user();
        Instant cutoff = now.minus(retention.duration());
        int purged = 0;
        while (true) {
            List<UUID> candidates = this.users.retainedBefore(cutoff);
            if (candidates.isEmpty()) {
                return purged;
            }
            int purgedThisBatch = 0;
            for (UUID userId : candidates) {
                if (this.transactions.write(() -> this.purgeIfStillEligible(userId, cutoff))) {
                    purged++;
                    purgedThisBatch++;
                }
            }
            if (purgedThisBatch == 0) {
                return purged;
            }
        }
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
        SubjectRef subject = IdentitySubjects.user(userId);
        this.grantAssignments.setRoles(subject, Set.of());
        this.grantAssignments.setStatements(subject, Set.of());
        this.memberships.setGroupsForUser(userId, Set.of());
        this.users.delete(user);
        return true;
    }
}
