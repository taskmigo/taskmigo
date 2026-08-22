package io.taskmigo.web.api;

import io.taskmigo.resource.PermissionCatalog;
import io.taskmigo.resource.ResourceService;
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
    ResourceController(ResourceService resources) { this.resources = resources; }

    @GetMapping("/permissions")
    Set<String> permissions() { return PermissionCatalog.ALL; }

    @PostMapping("/organizations")
    ResponseEntity<Map<String, UUID>> createOrganization(@Valid @RequestBody OrganizationRequest request) {
        UUID id = resources.createOrganization(request.key(), request.name());
        return created("/api/v0/organizations/" + id, id);
    }

    @PostMapping("/organizations/{organizationId}/users")
    ResponseEntity<Map<String, UUID>> createUser(@PathVariable UUID organizationId, @Valid @RequestBody UserRequest request) {
        UUID id = resources.createUser(organizationId, request.username(), request.email(), request.displayName());
        return created("/api/v0/users/" + id, id);
    }

    @PostMapping("/organizations/{organizationId}/groups")
    ResponseEntity<Map<String, UUID>> createGroup(@PathVariable UUID organizationId, @Valid @RequestBody NamedRequest request) {
        UUID id = resources.createGroup(organizationId, request.name(), request.description());
        return created("/api/v0/groups/" + id, id);
    }

    @PutMapping("/groups/{groupId}/members/{userId}")
    ResponseEntity<Void> addGroupMember(@PathVariable UUID groupId, @PathVariable UUID userId) {
        resources.addGroupMember(groupId, userId); return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/groups/{groupId}/members/{userId}")
    ResponseEntity<Void> removeGroupMember(@PathVariable UUID groupId, @PathVariable UUID userId) {
        resources.removeGroupMember(groupId, userId); return ResponseEntity.noContent().build();
    }

    @PostMapping("/organizations/{organizationId}/roles")
    ResponseEntity<Map<String, UUID>> createRole(@PathVariable UUID organizationId, @Valid @RequestBody RoleRequest request) {
        UUID id = resources.createRole(organizationId, request.name(), request.description(), request.permissions());
        return created("/api/v0/roles/" + id, id);
    }

    @PostMapping("/organizations/{organizationId}/projects")
    ResponseEntity<Map<String, UUID>> createProject(@PathVariable UUID organizationId, @Valid @RequestBody ProjectRequest request) {
        UUID id = resources.createProject(organizationId, request.key(), request.name(), request.description());
        return created("/api/v0/projects/" + id, id);
    }

    @PatchMapping("/projects/{projectId}/archive")
    ResponseEntity<Void> archiveProject(@PathVariable UUID projectId) {
        resources.archiveProject(projectId); return ResponseEntity.noContent().build();
    }

    @PostMapping("/projects/{projectId}/members")
    ResponseEntity<Map<String, UUID>> addProjectMember(@PathVariable UUID projectId, @Valid @RequestBody ProjectMemberRequest request) {
        UUID id = resources.addProjectMember(projectId, request.principalType(), Objects.requireNonNull(request.principalId()));
        return created("/api/v0/projects/" + projectId + "/members/" + id, id);
    }

    @DeleteMapping("/projects/{projectId}/members/{projectMemberId}")
    ResponseEntity<Void> removeProjectMember(@PathVariable UUID projectId, @PathVariable UUID projectMemberId) {
        resources.removeProjectMember(projectId, projectMemberId); return ResponseEntity.noContent().build();
    }

    @PutMapping("/projects/{projectId}/members/{projectMemberId}/roles")
    ResponseEntity<Void> setProjectMemberRoles(@PathVariable UUID projectId, @PathVariable UUID projectMemberId,
                                               @Valid @RequestBody RoleAssignmentRequest request) {
        resources.setProjectMemberRoles(projectId, projectMemberId, request.roleIds()); return ResponseEntity.noContent().build();
    }

    @GetMapping("/projects/{projectId}/users/{userId}/effective-permissions")
    Set<String> effectivePermissions(@PathVariable UUID projectId, @PathVariable UUID userId) {
        return resources.effectivePermissions(projectId, userId);
    }

    private static ResponseEntity<Map<String, UUID>> created(String location, UUID id) {
        return ResponseEntity.created(URI.create(location)).body(Map.of("id", id));
    }

    record OrganizationRequest(@NotBlank @Nullable String key, @NotBlank @Nullable String name) {}
    record UserRequest(@NotBlank @Nullable String username, @Email @NotBlank @Nullable String email, @NotBlank @Nullable String displayName) {}
    record NamedRequest(@NotBlank @Nullable String name, @Nullable String description) {}
    record RoleRequest(@NotBlank @Nullable String name, @Nullable String description, @Nullable Set<String> permissions) {}
    record ProjectRequest(@NotBlank @Nullable String key, @NotBlank @Nullable String name, @Nullable String description) {}
    record ProjectMemberRequest(@NotBlank @Nullable String principalType, @NotNull @Nullable UUID principalId) {}
    record RoleAssignmentRequest(@Nullable Set<UUID> roleIds) {}
}
