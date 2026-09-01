package io.taskmigo.authorization;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AuthorizationGroupStatementRepository extends JpaRepository<AuthorizationGroupStatementEdge, UUID> {
    List<AuthorizationGroupStatementEdge> findAllByGroupIdIn(Collection<UUID> groupIds);

    void deleteAllByGroupId(UUID groupId);
}
