package io.taskmigo.authorization;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthorizationGroupInheritanceRepository extends JpaRepository<AuthorizationGroupInheritanceEdge, UUID> {
    List<AuthorizationGroupInheritanceEdge> findAllByGroupIdIn(Collection<UUID> groupIds);

    void deleteAllByGroupId(UUID groupId);
}
