package io.taskmigo.identity.membership.adapter.out.persistence;

import io.taskmigo.identity.membership.adapter.out.persistence.GroupMembershipEntity.GroupMembershipId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaMembershipRepository extends JpaRepository<GroupMembershipEntity, GroupMembershipId> {
    @SuppressWarnings("checkstyle:SpringDataQuery")
    @Query(
        """
        select membership.id.groupId
        from GroupMembershipEntity membership
        where membership.id.userId = :userId
        order by membership.id.groupId
        """
    )
    List<UUID> findGroupIdsByUserId(@Param("userId") UUID userId);
}
