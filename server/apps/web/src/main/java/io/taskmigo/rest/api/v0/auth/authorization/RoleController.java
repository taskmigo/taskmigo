package io.taskmigo.rest.api.v0.auth.authorization;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationService;
import io.taskmigo.auth.authorization.statement.StatementService;
import io.taskmigo.auth.role.RoleAuthorizationService;
import io.taskmigo.auth.role.RoleInfo;
import io.taskmigo.auth.role.RoleService;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.rest.api.v0.support.pagination.OffsetPageRequest;
import io.taskmigo.rest.api.v0.support.response.ApiResponse;
import io.taskmigo.rest.api.v0.support.response.ApiResponseFactory;
import io.taskmigo.rest.support.objectauthorization.AuthorizationOperation;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(version = "0")
@Tag(name = "Role")
class RoleController {

    private final RoleService access;
    private final RoleAuthorizationService roleAuthorization;
    private final StatementService statements;
    private final ObjectAuthorizationService objectAuthorization;
    private final ApiResponseFactory responses;

    RoleController(
        RoleService access,
        RoleAuthorizationService roleAuthorization,
        StatementService statements,
        ObjectAuthorizationService objectAuthorization,
        ApiResponseFactory responses
    ) {
        this.access = access;
        this.roleAuthorization = roleAuthorization;
        this.statements = statements;
        this.objectAuthorization = objectAuthorization;
        this.responses = responses;
    }

    @PatchMapping("/roles/{roleId}/statements")
    @Operation(summary = "Replace a role's direct statements")
    @Transactional
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> setStatements(
        @PathVariable UUID roleId,
        @Valid @RequestBody StatementAssignmentRequest request
    ) {
        Set<UUID> statementIds = request.statementIds() == null ? Set.of() : request.statementIds();
        this.statements.requireStatements(statementIds);
        this.roleAuthorization.setStatements(roleId, statementIds);
        return this.responses.ok("resource.role.statements.updated", "Role statements updated");
    }

    @GetMapping("/roles")
    @Operation(summary = "List roles")
    ResponseEntity<ApiResponse<List<RoleInfo>, ApiResponse.OffsetMeta>> list(
        @ParameterObject @Valid OffsetPageRequest pagination,
        AuthorizationOperation authorization
    ) {
        OffsetPage<RoleInfo> roles = this.access.listRoles(
            pagination.page(),
            pagination.pageSize(),
            this.objectAuthorization.plan(authorization.snapshot(), authorization.method(), authorization.path())
        );
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
        UUID id = this.access.createRole(request.name(), request.description(), request.roleIds());
        return this.responses.created(
            URI.create("/api/v0/roles/" + id),
            Map.of("id", id),
            "resource.role.created",
            "Role created"
        );
    }

    @Schema(name = "CreateRoleRequest")
    record Request(@NotBlank @Nullable String name, @Nullable String description, @Nullable Set<UUID> roleIds) {}

    @Schema(name = "ReplaceRoleStatementsRequest")
    record StatementAssignmentRequest(@Nullable Set<UUID> statementIds) {}
}
