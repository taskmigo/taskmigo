package io.taskmigo.identity.membership.application;

import io.taskmigo.identity.membership.application.internal.MembershipCleanupService;
import java.util.UUID;

/// Performs deletion-only membership cleanup without weakening normal User mutation invariants.
public final class DefaultMembershipCleanupService implements MembershipCleanupService {

    private final MembershipRepository memberships;

    public DefaultMembershipCleanupService(MembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    public boolean removeAllForUser(UUID userId) {
        return this.memberships.removeAllForUser(userId);
    }
}
