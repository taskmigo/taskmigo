package io.taskmigo.authorization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthorizationUserRoleRepository extends JpaRepository<AuthorizationUserRoleAssignment, UUID> {
    List<AuthorizationUserRoleAssignment> findAllByUserId(UUID userId);

    boolean existsByUserIdAndRoleId(UUID userId, UUID roleId);
}
