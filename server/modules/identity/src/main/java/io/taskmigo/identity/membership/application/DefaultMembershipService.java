package io.taskmigo.identity.membership.application;

import io.taskmigo.identity.group.GroupException;
import io.taskmigo.identity.group.application.GroupQueryRepository;
import io.taskmigo.identity.membership.MembershipService;
import io.taskmigo.identity.user.UserService;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Coordinates Membership commands while keeping User/Group existence checks at the application boundary.
@Service
public class DefaultMembershipService implements MembershipService {

    private final MembershipRepository memberships;
    private final GroupQueryRepository groups;
    private final UserService users;

    public DefaultMembershipService(MembershipRepository memberships, GroupQueryRepository groups, UserService users) {
        this.memberships = memberships;
        this.groups = groups;
        this.users = users;
    }

    @Override
    @Transactional
    public void addMember(UUID groupId, UUID userId) {
        this.requireGroups(Set.of(groupId));
        this.users.require(userId);
        this.memberships.add(groupId, userId);
    }

    @Override
    @Transactional
    public void setGroupsForUser(UUID userId, Collection<UUID> groupIds) {
        this.users.require(userId);
        Set<UUID> requested = Set.copyOf(groupIds);
        this.requireGroups(requested);
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

    @Override
    @Transactional(readOnly = true)
    public List<UUID> groupsForUser(UUID userId) {
        return this.memberships.groupsForUser(userId);
    }

    private void requireGroups(Collection<UUID> ids) {
        if (!this.groups.containsAll(ids)) {
            throw new GroupException(GroupException.Type.INVALID_INPUT, "One or more Groups do not exist");
        }
    }
}
