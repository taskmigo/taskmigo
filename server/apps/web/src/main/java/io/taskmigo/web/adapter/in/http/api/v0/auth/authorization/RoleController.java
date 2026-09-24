package io.taskmigo.web.adapter.in.http.api.v0.auth.authorization;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.authorization.role.application.port.in.api.RoleAuthorizationService;
import io.taskmigo.authorization.role.application.port.in.api.RoleService;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.web.adapter.in.http.api.v0.support.pagination.OffsetPageRequest;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiResponse;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiResponseFactory;
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
    private final ApiResponseFactory responses;

    RoleController(RoleService access, RoleAuthorizationService roleAuthorization, ApiResponseFactory responses) {
        this.access = access;
        this.roleAuthorization = roleAuthorization;
        this.responses = responses;
    }

    @PatchMapping("/roles/{roleId}/statements")
    @Operation(summary = "Replace a role's direct statements")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> setStatements(
        @PathVariable UUID roleId,
        @Valid @RequestBody StatementAssignmentRequest request
    ) {
        this.roleAuthorization.setStatements(
            roleId,
            request.statementIds() == null ? Set.of() : request.statementIds()
        );
        return this.responses.ok("resource.role.statements.updated", "Role statements updated");
    }

    @GetMapping("/roles")
    @Operation(summary = "List roles")
    @Parameters({
        @Parameter(
            name = "page",
            in = ParameterIn.QUERY,
            description = "Page number to retrieve (1-based index)",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "1", minimum = "1")
        ),
        @Parameter(
            name = "pageSize",
            in = ParameterIn.QUERY,
            description = "Number of items per page",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "20", minimum = "1", maximum = "100")
        ),
    })
    ResponseEntity<ApiResponse<List<Response>, ApiResponse.OffsetMeta>> list(
        @Parameter(hidden = true) @Valid OffsetPageRequest pagination,
        FilteredQuery<RoleInfo> filter,
        ObjectAuthorizationPredicate<RoleInfo> authorization
    ) {
        OffsetPage<RoleInfo> roles = this.access.listRoles(
            pagination.getPage(),
            pagination.getPageSize(),
            filter.predicate(),
            authorization
        );
        return this.responses.ok(
            roles.items().stream().map(Response::from).toList(),
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
            request.code(),
            request.displayName(),
            request.description(),
            request.roleIds()
        );
        return this.responses.created(
            URI.create("/api/v0/roles/" + id),
            Map.of("id", id),
            "resource.role.created",
            "Role created"
        );
    }

    @Schema(name = "RoleInfo")
    record Response(
        UUID id,
        String code,
        String displayName,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String description,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<Response> children
    ) {
        static Response from(RoleInfo role) {
            return new Response(
                role.id(),
                role.code(),
                role.displayName(),
                role.description(),
                role.children().stream().map(Response::from).toList()
            );
        }
    }

    @Schema(name = "CreateRoleRequest")
    record Request(
        @NotBlank @Nullable String code,
        @NotBlank @Nullable String displayName,
        @Nullable String description,
        @Nullable Set<@NotNull UUID> roleIds
    ) {}

    @Schema(name = "ReplaceRoleStatementsRequest")
    record StatementAssignmentRequest(@Nullable Set<@NotNull UUID> statementIds) {}
}
