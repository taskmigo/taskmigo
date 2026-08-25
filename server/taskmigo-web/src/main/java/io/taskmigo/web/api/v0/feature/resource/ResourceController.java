package io.taskmigo.web.api.v0.feature.resource;

import io.taskmigo.history.ProjectHistory;
import io.taskmigo.resource.PermissionCatalog;
import io.taskmigo.resource.ProjectChanged;
import io.taskmigo.resource.ResourceService;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
import org.springframework.validation.annotation.Validated;
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
@Validated
class ResourceController {

    private final ResourceService resources;
    private final ProjectHistory history;
    private final ApiResponseFactory responses;

    ResourceController(ResourceService resources, ProjectHistory history, ApiResponseFactory responses) {
        this.resources = resources;
        this.history = history;
        this.responses = responses;
    }

    @GetMapping("/permissions")
    ResponseEntity<ApiResponse<Set<String>, ApiResponse.BasicMeta>> permissions() {
        return this.responses.ok(PermissionCatalog.ALL, "resource.permissions.retrieved", "Permissions retrieved");
    }

    @PostMapping("/organizations")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> createOrganization(
        @Valid @RequestBody OrganizationRequest request
    ) {
        UUID id = this.resources.createOrganization(request.key(), request.name());
        return this.responses.created(
            URI.create("/api/v0/organizations/" + id),
            Map.of("id", id),
            "resource.organization.created",
            "Organization created"
        );
    }

    @PostMapping("/organizations/{organizationId}/users")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> createUser(
        @PathVariable UUID organizationId,
        @Valid @RequestBody UserRequest request
    ) {
        UUID id = this.resources.createUser(organizationId, request.username(), request.email(), request.displayName());
        return this.responses.created(
            URI.create("/api/v0/users/" + id),
            Map.of("id", id),
            "resource.user.created",
            "User created"
        );
    }

    @PostMapping("/organizations/{organizationId}/groups")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> createGroup(
        @PathVariable UUID organizationId,
        @Valid @RequestBody NamedRequest request
    ) {
        UUID id = this.resources.createGroup(organizationId, request.name(), request.description());
        return this.responses.created(
            URI.create("/api/v0/groups/" + id),
            Map.of("id", id),
            "resource.group.created",
            "Group created"
        );
    }

    @PutMapping("/groups/{groupId}/members/{userId}")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> addGroupMember(
        @PathVariable UUID groupId,
        @PathVariable UUID userId
    ) {
        this.resources.addGroupMember(groupId, userId);
        return this.responses.ok("resource.group.member_added", "Group member added");
    }

    @DeleteMapping("/groups/{groupId}/members/{userId}")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> removeGroupMember(
        @PathVariable UUID groupId,
        @PathVariable UUID userId
    ) {
        this.resources.removeGroupMember(groupId, userId);
        return this.responses.ok("resource.group.member_removed", "Group member removed");
    }

    @PostMapping("/organizations/{organizationId}/roles")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> createRole(
        @PathVariable UUID organizationId,
        @Valid @RequestBody RoleRequest request
    ) {
        UUID id = this.resources.createRole(
            organizationId,
            request.name(),
            request.description(),
            request.permissions()
        );
        return this.responses.created(
            URI.create("/api/v0/roles/" + id),
            Map.of("id", id),
            "resource.role.created",
            "Role created"
        );
    }

    @PostMapping("/organizations/{organizationId}/projects")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> createProject(
        @PathVariable UUID organizationId,
        @Valid @RequestBody ProjectRequest request,
        Authentication authentication
    ) {
        UUID id = this.resources.createProject(
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
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> archiveProject(
        @PathVariable UUID projectId,
        Authentication authentication
    ) {
        this.resources.archiveProject(projectId, actor(authentication));
        return this.responses.ok("resource.project.archived", "Project archived");
    }

    @PostMapping("/projects/{projectId}/members")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> addProjectMember(
        @PathVariable UUID projectId,
        @Valid @RequestBody ProjectMemberRequest request,
        Authentication authentication
    ) {
        UUID id = this.resources.addProjectMember(
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
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> removeProjectMember(
        @PathVariable UUID projectId,
        @PathVariable UUID projectMemberId,
        Authentication authentication
    ) {
        this.resources.removeProjectMember(projectId, projectMemberId, actor(authentication));
        return this.responses.ok("resource.project.member_removed", "Project member removed");
    }

    @PutMapping("/projects/{projectId}/members/{projectMemberId}/roles")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> setProjectMemberRoles(
        @PathVariable UUID projectId,
        @PathVariable UUID projectMemberId,
        @Valid @RequestBody RoleAssignmentRequest request,
        Authentication authentication
    ) {
        this.resources.setProjectMemberRoles(projectId, projectMemberId, request.roleIds(), actor(authentication));
        return this.responses.ok("resource.project.member_roles_updated", "Project member roles updated");
    }

    @GetMapping("/projects/{projectId}/history")
    ResponseEntity<ApiResponse<List<ProjectHistory.Entry>, ApiResponse.CursorMeta>> projectHistory(
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
            this.resources.effectivePermissions(projectId, userId),
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

    record OrganizationRequest(@NotBlank @Nullable String key, @NotBlank @Nullable String name) {}

    record UserRequest(
        @NotBlank @Nullable String username,
        @Email @NotBlank @Nullable String email,
        @NotBlank @Nullable String displayName
    ) {}

    record NamedRequest(@NotBlank @Nullable String name, @Nullable String description) {}

    record RoleRequest(
        @NotBlank @Nullable String name,
        @Nullable String description,
        @Nullable Set<String> permissions
    ) {}

    record ProjectRequest(
        @NotBlank @Nullable String key,
        @NotBlank @Nullable String name,
        @Nullable String description
    ) {}

    record ProjectMemberRequest(@NotBlank @Nullable String principalType, @NotNull @Nullable UUID principalId) {}

    record RoleAssignmentRequest(@Nullable Set<UUID> roleIds) {}
}
