package io.taskmigo.group;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

interface GroupRepository extends JpaRepository<GroupEntity, UUID> {
    @SuppressWarnings("checkstyle:SpringDataQuery")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entity from GroupEntity entity")
    List<GroupEntity> findAllForUpdate();

    List<GroupEntity> findAllByMemberIdsContains(UUID userId);
}
