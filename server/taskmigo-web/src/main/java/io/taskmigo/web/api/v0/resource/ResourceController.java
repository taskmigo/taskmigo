package io.taskmigo.web.api.v0.resource;

import io.taskmigo.resource.PermissionCatalog;
import io.taskmigo.resource.ResourceService;
import io.taskmigo.web.api.v0.response.ApiResponse;
import io.taskmigo.web.api.v0.response.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v0")
class ResourceController {

    private final ResourceService resources;
    private final ApiResponseFactory responses;

    ResourceController(ResourceService resources, ApiResponseFactory responses) {
        this.resources = resources;
        this.responses = responses;
    }

    @GetMapping("/permissions")
    ResponseEntity<ApiResponse<Set<String>>> permissions() {
        return this.responses.ok(PermissionCatalog.ALL, "resource.permissions.retrieved", "Permissions retrieved");
    }

    @PostMapping("/organizations")
    ResponseEntity<ApiResponse<Map<String, UUID>>> createOrganization(@Valid @RequestBody OrganizationRequest request) {
        UUID id = this.resources.createOrganization(request.key(), request.name());
        return this.responses.created(
            URI.create("/api/v0/organizations/" + id),
            Map.of("id", id),
            "resource.organization.created",
            "Organization created"
        );
    }

    @PostMapping("/organizations/{organizationId}/users")
    ResponseEntity<ApiResponse<Map<String, UUID>>> createUser(
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
    ResponseEntity<ApiResponse<Map<String, UUID>>> createGroup(
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
    ResponseEntity<ApiResponse<Void>> addGroupMember(@PathVariable UUID groupId, @PathVariable UUID userId) {
        this.resources.addGroupMember(groupId, userId);
        return this.responses.ok("resource.group.member_added", "Group member added");
    }

    @DeleteMapping("/groups/{groupId}/members/{userId}")
    ResponseEntity<ApiResponse<Void>> removeGroupMember(@PathVariable UUID groupId, @PathVariable UUID userId) {
        this.resources.removeGroupMember(groupId, userId);
        return this.responses.ok("resource.group.member_removed", "Group member removed");
    }

    @PostMapping("/organizations/{organizationId}/roles")
    ResponseEntity<ApiResponse<Map<String, UUID>>> createRole(
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
    ResponseEntity<ApiResponse<Map<String, UUID>>> createProject(
        @PathVariable UUID organizationId,
        @Valid @RequestBody ProjectRequest request
    ) {
        UUID id = this.resources.createProject(organizationId, request.key(), request.name(), request.description());
        return this.responses.created(
            URI.create("/api/v0/projects/" + id),
            Map.of("id", id),
            "resource.project.created",
            "Project created"
        );
    }

    @PatchMapping("/projects/{projectId}/archive")
    ResponseEntity<ApiResponse<Void>> archiveProject(@PathVariable UUID projectId) {
        this.resources.archiveProject(projectId);
        return this.responses.ok("resource.project.archived", "Project archived");
    }

    @PostMapping("/projects/{projectId}/members")
    ResponseEntity<ApiResponse<Map<String, UUID>>> addProjectMember(
        @PathVariable UUID projectId,
        @Valid @RequestBody ProjectMemberRequest request
    ) {
        UUID id = this.resources.addProjectMember(
            projectId,
            request.principalType(),
            Objects.requireNonNull(request.principalId())
        );
        return this.responses.created(
            URI.create("/api/v0/projects/" + projectId + "/members/" + id),
            Map.of("id", id),
            "resource.project.member_added",
            "Project member added"
        );
    }

    @DeleteMapping("/projects/{projectId}/members/{projectMemberId}")
    ResponseEntity<ApiResponse<Void>> removeProjectMember(
        @PathVariable UUID projectId,
        @PathVariable UUID projectMemberId
    ) {
        this.resources.removeProjectMember(projectId, projectMemberId);
        return this.responses.ok("resource.project.member_removed", "Project member removed");
    }

    @PutMapping("/projects/{projectId}/members/{projectMemberId}/roles")
    ResponseEntity<ApiResponse<Void>> setProjectMemberRoles(
        @PathVariable UUID projectId,
        @PathVariable UUID projectMemberId,
        @Valid @RequestBody RoleAssignmentRequest request
    ) {
        this.resources.setProjectMemberRoles(projectId, projectMemberId, request.roleIds());
        return this.responses.ok("resource.project.member_roles_updated", "Project member roles updated");
    }

    @GetMapping("/projects/{projectId}/users/{userId}/effective-permissions")
    ResponseEntity<ApiResponse<Set<String>>> effectivePermissions(
        @PathVariable UUID projectId,
        @PathVariable UUID userId
    ) {
        return this.responses.ok(
            this.resources.effectivePermissions(projectId, userId),
            "resource.project.effective_permissions_retrieved",
            "Effective permissions retrieved"
        );
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
