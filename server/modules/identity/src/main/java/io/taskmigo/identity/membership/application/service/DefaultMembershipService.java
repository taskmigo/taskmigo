package io.taskmigo.identity.membership.application.service;

import io.taskmigo.identity.application.port.out.TransactionRunner;
import io.taskmigo.identity.group.application.port.in.api.GroupService;
import io.taskmigo.identity.membership.application.port.in.api.MembershipService;
import io.taskmigo.identity.membership.application.port.out.MembershipRepository;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/// Coordinates Membership commands through Group/User inbound ports and a targeted Membership outbound port.
public final class DefaultMembershipService implements MembershipService {

    private final MembershipRepository memberships;
    private final GroupService groups;
    private final UserService users;
    private final TransactionRunner transactions;

    public DefaultMembershipService(
        MembershipRepository memberships,
        GroupService groups,
        UserService users,
        TransactionRunner transactions
    ) {
        this.memberships = memberships;
        this.groups = groups;
        this.users = users;
        this.transactions = transactions;
    }

    @Override
    public void addMember(UUID groupId, UUID userId) {
        this.transactions.write(() -> {
            this.groups.requireGroups(Set.of(groupId));
            this.users.require(userId);
            this.memberships.add(groupId, userId);
        });
    }

    @Override
    public void setGroupsForUser(UUID userId, Collection<UUID> groupIds) {
        this.transactions.write(() -> {
            this.users.require(userId);
            Set<UUID> requested = Set.copyOf(groupIds);
            this.groups.requireGroups(requested);
            Set<UUID> current = Set.copyOf(this.memberships.groupsForUser(userId));
            requested
                .stream()
                .filter(groupId -> !current.contains(groupId))
                .forEach(groupId -> this.memberships.add(groupId, userId));
            current
                .stream()
                .filter(groupId -> !requested.contains(groupId))
                .forEach(groupId -> this.memberships.remove(groupId, userId));
        });
    }

    @Override
    public List<UUID> groupsForUser(UUID userId) {
        return this.transactions.read(() -> this.memberships.groupsForUser(userId));
    }
}
