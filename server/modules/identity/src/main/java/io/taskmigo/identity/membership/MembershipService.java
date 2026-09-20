package io.taskmigo.identity.membership;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/// Defines published Identity membership commands and direct-membership queries.
public interface MembershipService {
    void addMember(UUID groupId, UUID userId);

    void setGroupsForUser(UUID userId, Collection<UUID> groupIds);

    List<UUID> groupsForUser(UUID userId);
}
