package io.taskmigo.identity.membership.application.port.out;

import java.util.List;
import java.util.UUID;

/// Persists targeted User-to-Group membership changes without loading Group aggregates or hierarchy state.
public interface MembershipRepository {
    void add(UUID groupId, UUID userId);

    void remove(UUID groupId, UUID userId);

    List<UUID> groupsForUser(UUID userId);
}
