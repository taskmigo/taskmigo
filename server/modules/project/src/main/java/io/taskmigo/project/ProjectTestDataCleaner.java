package io.taskmigo.project;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Hard-deletes explicitly owned project records for isolated test cleanup.
@Service
public class ProjectTestDataCleaner {

    private final ProjectRepository projects;

    ProjectTestDataCleaner(ProjectRepository projects) {
        this.projects = projects;
    }

    /// Deletes only the project identifiers supplied by the test ownership scope.
    @Transactional
    public void purge(Set<UUID> ids) {
        if (!ids.isEmpty()) this.projects.deleteAllByIdInBatch(ids);
    }
}
