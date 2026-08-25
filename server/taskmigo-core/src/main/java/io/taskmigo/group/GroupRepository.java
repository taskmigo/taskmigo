package io.taskmigo.group;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface GroupRepository extends JpaRepository<GroupEntity, UUID> {
    @Query("select g.id from GroupEntity g join g.memberIds m where m = :userId")
    List<UUID> findIdsContainingUser(@Param("userId") UUID userId);
}
