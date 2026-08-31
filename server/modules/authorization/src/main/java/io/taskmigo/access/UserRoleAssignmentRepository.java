package io.taskmigo.access;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignmentEntity, UUID> {
    List<UserRoleAssignmentEntity> findAllByUserId(UUID userId);
}
