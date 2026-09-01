package io.taskmigo.authorization;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthorizationRoleStatementRepository extends JpaRepository<AuthorizationRoleStatementEdge, UUID> {
    List<AuthorizationRoleStatementEdge> findAllByRoleIdIn(Collection<UUID> roleIds);

    void deleteAllByRoleId(UUID roleId);
}
