package io.taskmigo.auth.group;

import jakarta.persistence.LockModeType;
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

    List<GroupEntity> findAllByMemberIdsContains(UUID userId);

    @EntityGraph(attributePaths = { "memberIds", "roleIds", "childGroups" })
    List<GroupEntity> findAllByOrderByIdAsc();
}
