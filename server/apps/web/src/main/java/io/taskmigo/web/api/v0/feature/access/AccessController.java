package io.taskmigo.web.api.v0.feature.access;

import io.taskmigo.access.AccessService;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import io.taskmigo.web.security.ApiAclSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v0")
class AccessController {

    private final AccessService access;
    private final ApiResponseFactory responses;
    private final ApiAclSupport acl;

    AccessController(AccessService access, ApiResponseFactory responses, ApiAclSupport acl) {
        this.access = access;
        this.responses = responses;
        this.acl = acl;
    }

    @GetMapping("/organizations/{organizationId}/statements")
    ResponseEntity<ApiResponse<List<AccessService.StatementInfo>, ApiResponse.BasicMeta>> statements(
        @PathVariable UUID organizationId,
        Authentication authentication
    ) {
        this.acl.requireOrganization(authentication, organizationId);
        return this.responses.ok(
            this.access.statements(organizationId),
            "resource.statements.retrieved",
            "Statements retrieved"
        );
    }

    @GetMapping("/organizations/{organizationId}/roles")
    ResponseEntity<ApiResponse<List<AccessService.RoleInfo>, ApiResponse.BasicMeta>> roles(
        @PathVariable UUID organizationId,
        Authentication authentication
    ) {
        this.acl.requireOrganization(authentication, organizationId);
        return this.responses.ok(this.access.roles(organizationId), "resource.roles.retrieved", "Roles retrieved");
    }

    @PostMapping("/organizations/{organizationId}/roles")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> createRole(
        @PathVariable UUID organizationId,
        @Valid @RequestBody RoleRequest request,
        Authentication authentication
    ) {
        this.acl.requireOrganization(authentication, organizationId);
        UUID id = this.access.createRole(
            organizationId,
            request.key(),
            request.name(),
            request.description(),
            request.statementIds()
        );
        return this.responses.created(
            URI.create("/api/v0/roles/" + id),
            Map.of("id", id),
            "resource.role.created",
            "Role created"
        );
    }

    @PatchMapping("/users/{userId}/roles")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> setUserRoles(
        @PathVariable UUID userId,
        @Valid @RequestBody RoleAssignmentRequest request,
        Authentication authentication
    ) {
        UUID organizationId = this.access.roleManagementOrganization(userId);
        this.acl.requireOrganization(authentication, organizationId);
        this.access.setUserRoles(userId, request.roleIds());
        return this.responses.ok("resource.user.roles_updated", "User Roles updated");
    }

    record RoleRequest(
        @NotBlank @Nullable String key,
        @NotBlank @Nullable String name,
        @Nullable String description,
        @Nullable Set<UUID> statementIds
    ) {}

    record RoleAssignmentRequest(@NotNull @Nullable Set<UUID> roleIds) {}
}
