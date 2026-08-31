package io.taskmigo.history;

import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface ProjectHistoryRepository
    extends JpaRepository<ProjectHistoryEntity, UUID>, JpaSpecificationExecutor<ProjectHistoryEntity> {
    void deleteAllByProjectIdIn(Set<UUID> projectIds);
}
