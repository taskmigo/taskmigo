package io.taskmigo.project;

import io.taskmigo.acl.ApiAclEngine.ResponsePlan;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Demonstrates response ACL pushdown: the ACL plan becomes a JPA Specification before rows are loaded.
@Service
public class ProjectAclQueryService {

    private final ProjectRepository projects;

    ProjectAclQueryService(ProjectRepository projects) {
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list(ResponsePlan plan) {
        return this.projects
            .findAll(ProjectAclSpecifications.from(plan), Sort.by("id"))
            .stream()
            .map(project ->
                new ProjectView(
                    project.id,
                    project.organizationId,
                    project.key,
                    project.name,
                    project.description,
                    project.status.name()
                )
            )
            .toList();
    }

    public record ProjectView(
        UUID id,
        UUID organizationId,
        String key,
        String name,
        @Nullable String description,
        String status
    ) {}
}
