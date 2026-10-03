package io.taskmigo.identity.membership.application.port.in.internal;

import java.util.UUID;

/// Removes User membership state for deletion lifecycle processing without treating the retained User as mutable.
public interface MembershipCleanupService {
    boolean removeAllForUser(UUID userId);
}
