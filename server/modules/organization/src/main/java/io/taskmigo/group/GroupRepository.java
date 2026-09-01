package io.taskmigo.group;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface GroupRepository extends JpaRepository<GroupEntity, UUID> {
    List<GroupEntity> findAllByMemberIdsContains(UUID userId);
}
