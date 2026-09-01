package io.taskmigo.web.api.v0.feature.access;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.access.AccessService;
import io.taskmigo.access.AccessService.RoleInfo;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.web.api.v0.infrastructure.pagination.OffsetPageRequest;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v0")
@Tag(name = "Role")
class RoleController {

    private final AccessService access;
    private final ApiResponseFactory responses;

    RoleController(AccessService access, ApiResponseFactory responses) {
        this.access = access;
        this.responses = responses;
    }

    @GetMapping("/roles")
    @Operation(summary = "List roles")
    ResponseEntity<ApiResponse<List<RoleInfo>, ApiResponse.OffsetMeta>> list(
        @ParameterObject @Valid OffsetPageRequest pagination
    ) {
        OffsetPage<RoleInfo> roles = this.access.listRoles(pagination.page(), pagination.pageSize());
        return this.responses.ok(
            roles.items(),
            new ApiResponse.OffsetPagination(pagination, roles),
            "resource.role.listed",
            "Roles listed"
        );
    }

    @PostMapping("/roles")
    @Operation(summary = "Create a role")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> createRole(
        @Valid @RequestBody Request request
    ) {
        UUID id = this.access.createRole(
            request.name(),
            request.description(),
            request.permissions(),
            request.roleIds()
        );
        return this.responses.created(
            URI.create("/api/v0/roles/" + id),
            Map.of("id", id),
            "resource.role.created",
            "Role created"
        );
    }

    @Schema(name = "CreateRoleRequest")
    record Request(
        @NotBlank @Nullable String name,
        @Nullable String description,
        @Nullable Set<String> permissions,
        @Nullable Set<UUID> roleIds
    ) {}
}
