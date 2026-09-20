package io.taskmigo.identity.membership.adapter.out.persistence;

import io.taskmigo.identity.membership.adapter.out.persistence.GroupMembershipEntity.GroupMembershipId;
import io.taskmigo.identity.membership.application.port.out.MembershipRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/// Applies targeted Membership changes directly to the membership table.
@Repository
public class JpaMembershipOperations implements MembershipRepository {

    private final JpaMembershipRepository memberships;

    JpaMembershipOperations(JpaMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Override
    public void add(UUID groupId, UUID userId) {
        GroupMembershipId id = new GroupMembershipId(groupId, userId);
        if (this.memberships.existsById(id)) {
            return;
        }
        this.memberships.saveAndFlush(new GroupMembershipEntity(groupId, userId));
    }

    @Override
    public void remove(UUID groupId, UUID userId) {
        GroupMembershipId id = new GroupMembershipId(groupId, userId);
        if (!this.memberships.existsById(id)) {
            return;
        }
        this.memberships.deleteById(id);
        this.memberships.flush();
    }

    @Override
    public List<UUID> groupsForUser(UUID userId) {
        return this.memberships.findGroupIdsByUserId(userId);
    }
}
