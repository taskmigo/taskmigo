package io.taskmigo.history;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Hard-deletes project history associated with explicitly owned projects during isolated test cleanup.
@Service
public class ProjectHistoryTestDataCleaner {

    private final ProjectHistoryRepository entries;

    ProjectHistoryTestDataCleaner(ProjectHistoryRepository entries) {
        this.entries = entries;
    }

    /// Deletes history only for projects supplied by the test ownership scope.
    @Transactional
    public void purgeProjects(Set<UUID> projectIds) {
        if (projectIds.isEmpty()) return;
        this.entries.deleteAllByProjectIdIn(projectIds);
        this.entries.flush();
    }
}
