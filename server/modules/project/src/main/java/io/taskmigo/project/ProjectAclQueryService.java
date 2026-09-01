package io.taskmigo.project;

import io.taskmigo.authorization.AuthorizationEngine.ObjectPlan;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAclQueryService {

    private final ProjectRepository projects;

    ProjectAclQueryService(ProjectRepository projects) {
        this.projects = projects;
    }

    @Transactional(readOnly = true)
    public List<ProjectView> list(ObjectPlan plan) {
        return this.projects
            .findAll(ProjectAclSpecifications.from(plan), Sort.by("id"))
            .stream()
            .map(ProjectAclQueryService::view)
            .toList();
    }

    private static ProjectView view(ProjectEntity project) {
        return new ProjectView(
            project.id,
            project.organizationId,
            project.key,
            project.name,
            project.description,
            project.status.name(),
            project.archivedAt
        );
    }

    public record ProjectView(
        UUID id,
        UUID organizationId,
        String key,
        String name,
        @Nullable String description,
        String status,
        @Nullable Instant archivedAt
    ) {
        public static final Set<String> FIELDS = Set.of(
            "id",
            "organizationId",
            "key",
            "name",
            "description",
            "status",
            "archivedAt"
        );

        public Map<String, Object> authorizationContext() {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("object.id", this.id);
            context.put("object.organizationId", this.organizationId);
            context.put("object.key", this.key);
            context.put("object.name", this.name);
            context.put("object.description", this.description);
            context.put("object.status", this.status);
            context.put("object.archivedAt", this.archivedAt == null ? null : this.archivedAt.toString());
            return context;
        }
    }
}
