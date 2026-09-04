package io.taskmigo.auth.group;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface GroupRepository extends JpaRepository<GroupEntity, UUID>, JpaSpecificationExecutor<GroupEntity> {
    @SuppressWarnings("checkstyle:SpringDataQuery")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from GroupEntity entity")
    List<GroupEntity> findAllForUpdate();

    @EntityGraph(attributePaths = "roleIds")
    List<GroupEntity> findDistinctByMemberIdsContains(UUID userId);

    @EntityGraph(attributePaths = "roleIds")
    List<GroupEntity> findDistinctByParentGroups_IdIn(Collection<UUID> parentIds);
}
