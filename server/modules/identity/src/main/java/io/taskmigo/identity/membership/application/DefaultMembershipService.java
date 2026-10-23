package io.taskmigo.identity.membership.application;

import io.taskmigo.identity.group.application.api.GroupService;
import io.taskmigo.identity.membership.application.api.MembershipService;
import io.taskmigo.identity.user.UserException;
import io.taskmigo.identity.user.application.internal.UserCommandService;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/// Coordinates Membership commands through Group/User application APIs and an application-owned repository.
@Transactional
public class DefaultMembershipService implements MembershipService {

    private final MembershipRepository memberships;
    private final GroupService groups;
    private final UserCommandService users;

    public DefaultMembershipService(MembershipRepository memberships, GroupService groups, UserCommandService users) {
        this.memberships = memberships;
        this.groups = groups;
        this.users = users;
    }

    @Override
    public void addMember(UUID groupId, UUID userId) {
        this.groups.requireGroups(Set.of(groupId));
        this.requireLocked(userId);
        this.memberships.add(groupId, userId);
    }

    @Override
    public void setGroupsForUser(UUID userId, Collection<UUID> groupIds) {
        Set<UUID> requested = Set.copyOf(groupIds);
        this.groups.requireGroups(requested);
        this.requireLocked(userId);
        Set<UUID> current = Set.copyOf(this.memberships.groupsForUser(userId));
        requested
            .stream()
            .filter(groupId -> !current.contains(groupId))
            .forEach(groupId -> this.memberships.add(groupId, userId));
        current
            .stream()
            .filter(groupId -> !requested.contains(groupId))
            .forEach(groupId -> this.memberships.remove(groupId, userId));
    }

    @Transactional(readOnly = true)
    @Override
    public List<UUID> groupsForUser(UUID userId) {
        return this.memberships.groupsForUser(userId);
    }

    private void requireLocked(UUID userId) {
        var user = this.users
            .findByIdForUpdate(userId)
            .orElseThrow(() -> new UserException(UserException.Type.NOT_FOUND, "User not found"));
        user.requireMutable();
    }
}
