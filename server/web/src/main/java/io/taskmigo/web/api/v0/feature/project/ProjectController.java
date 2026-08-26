package io.taskmigo.web.api.v0.feature.project;

import io.taskmigo.history.ProjectHistory;
import io.taskmigo.project.ProjectChanged;
import io.taskmigo.project.ProjectService;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v0")
class ProjectController {

    private final ProjectService projects;
    private final ProjectHistory history;
    private final ApiResponseFactory responses;

    ProjectController(ProjectService projects, ProjectHistory history, ApiResponseFactory responses) {
        this.projects = projects;
        this.history = history;
        this.responses = responses;
    }

    @PostMapping("/organizations/{organizationId}/projects")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(
        @PathVariable UUID organizationId,
        @Valid @RequestBody ProjectRequest request,
        Authentication authentication
    ) {
        UUID id = this.projects.create(
            organizationId,
            request.key(),
            request.name(),
            request.description(),
            actor(authentication)
        );
        return this.responses.created(
            URI.create("/api/v0/projects/" + id),
            Map.of("id", id),
            "resource.project.created",
            "Project created"
        );
    }

    @PatchMapping("/projects/{projectId}/archive")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> archive(
        @PathVariable UUID projectId,
        Authentication authentication
    ) {
        this.projects.archive(projectId, actor(authentication));
        return this.responses.ok("resource.project.archived", "Project archived");
    }

    @PostMapping("/projects/{projectId}/members")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> addMember(
        @PathVariable UUID projectId,
        @Valid @RequestBody MemberRequest request,
        Authentication authentication
    ) {
        UUID id = this.projects.addMember(
            projectId,
            request.principalType(),
            Objects.requireNonNull(request.principalId()),
            actor(authentication)
        );
        return this.responses.created(
            URI.create("/api/v0/projects/" + projectId + "/members/" + id),
            Map.of("id", id),
            "resource.project.member_added",
            "Project member added"
        );
    }

    @DeleteMapping("/projects/{projectId}/members/{projectMemberId}")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> removeMember(
        @PathVariable UUID projectId,
        @PathVariable UUID projectMemberId,
        Authentication authentication
    ) {
        this.projects.removeMember(projectId, projectMemberId, actor(authentication));
        return this.responses.ok("resource.project.member_removed", "Project member removed");
    }

    @PutMapping("/projects/{projectId}/members/{projectMemberId}/roles")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> setMemberRoles(
        @PathVariable UUID projectId,
        @PathVariable UUID projectMemberId,
        @Valid @RequestBody RoleAssignmentRequest request,
        Authentication authentication
    ) {
        this.projects.setMemberRoles(projectId, projectMemberId, request.roleIds(), actor(authentication));
        return this.responses.ok("resource.project.member_roles_updated", "Project member roles updated");
    }

    @GetMapping("/projects/{projectId}/history")
    ResponseEntity<ApiResponse<List<ProjectHistory.Entry>, ApiResponse.CursorMeta>> history(
        @PathVariable UUID projectId,
        @RequestParam(required = false) @Nullable String cursor,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        ProjectHistory.Page page = this.history.list(projectId, cursor, limit);
        String nextCursor = page.nextCursor();
        return this.responses.ok(
            page.items(),
            new ApiResponse.CursorPagination(new ApiResponse.Cursor(nextCursor, null, nextCursor != null)),
            "resource.project.history_retrieved",
            "Project history retrieved"
        );
    }

    @GetMapping("/projects/{projectId}/users/{userId}/effective-permissions")
    ResponseEntity<ApiResponse<Set<String>, ApiResponse.BasicMeta>> effectivePermissions(
        @PathVariable UUID projectId,
        @PathVariable UUID userId
    ) {
        return this.responses.ok(
            this.projects.effectivePermissions(projectId, userId),
            "resource.project.effective_permissions_retrieved",
            "Effective permissions retrieved"
        );
    }

    private static ProjectChanged.Actor actor(Authentication authentication) {
        String id = authentication.getName();
        String displayName = id;
        ProjectChanged.ActorType type = ProjectChanged.ActorType.USER;
        if (authentication instanceof JwtAuthenticationToken token) {
            String subject = token.getToken().getSubject();
            if (subject != null && !subject.isBlank()) id = subject;
            String name = token.getToken().getClaimAsString("name");
            if (name == null || name.isBlank()) name = token.getToken().getClaimAsString("preferred_username");
            if (name != null && !name.isBlank()) displayName = name;
            String clientId = token.getToken().getClaimAsString("client_id");
            if (clientId != null && clientId.equals(id)) type = ProjectChanged.ActorType.SERVICE;
        }
        return new ProjectChanged.Actor(type, id, displayName);
    }

    record ProjectRequest(
        @NotBlank @Nullable String key,
        @NotBlank @Nullable String name,
        @Nullable String description
    ) {}

    record MemberRequest(@NotBlank @Nullable String principalType, @NotNull @Nullable UUID principalId) {}

    record RoleAssignmentRequest(@Nullable Set<UUID> roleIds) {}
}
