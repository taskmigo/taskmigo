package io.taskmigo.web.api.v0.feature.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.auth.AccessService;
import io.taskmigo.auth.GroupService;
import io.taskmigo.auth.ObjectAuthorizationService;
import io.taskmigo.auth.StatementService;
import io.taskmigo.auth.UserService;
import io.taskmigo.auth.UserService.UserInfo;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.web.api.v0.infrastructure.pagination.OffsetPageRequest;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.web.api.v0.infrastructure.response.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v0/users")
@Tag(name = "User")
class UserController {

    private final UserService users;
    private final AccessService access;
    private final GroupService groups;
    private final StatementService statements;
    private final ObjectAuthorizationService objectAuthorization;
    private final ApiResponseFactory responses;

    UserController(
        UserService users,
        AccessService access,
        GroupService groups,
        StatementService statements,
        ObjectAuthorizationService objectAuthorization,
        ApiResponseFactory responses
    ) {
        this.users = users;
        this.access = access;
        this.groups = groups;
        this.statements = statements;
        this.objectAuthorization = objectAuthorization;
        this.responses = responses;
    }

    @GetMapping
    @Operation(summary = "List users")
    ResponseEntity<ApiResponse<java.util.List<UserInfo>, ApiResponse.OffsetMeta>> list(
        @ParameterObject @Valid OffsetPageRequest pagination,
        @AuthenticationPrincipal @Nullable Jwt jwt
    ) {
        OffsetPage<UserInfo> users = this.users.list(
            pagination.page(),
            pagination.pageSize(),
            io.taskmigo.web.security.ObjectAuthorizationContext.plan(
                this.objectAuthorization,
                jwt,
                "GET",
                "/api/v0/users"
            )
        );
        return this.responses.ok(
            users.items(),
            new ApiResponse.OffsetPagination(pagination, users),
            "resource.user.listed",
            "Users listed"
        );
    }

    @PatchMapping("/{userId}/statements")
    @Transactional
    @Operation(summary = "Replace a user's direct statements")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> setStatements(
        @PathVariable UUID userId,
        @Valid @RequestBody StatementAssignmentRequest request
    ) {
        Set<UUID> statementIds = request.statementIds() == null ? Set.of() : request.statementIds();
        this.statements.requireStatements(statementIds);
        this.users.setStatements(userId, statementIds);
        return this.responses.ok("resource.user.statements.updated", "User statements updated");
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Create a new user")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(@Valid @RequestBody Request request) {
        Set<UUID> roleIds = request.roleIds() == null ? Set.of() : request.roleIds();
        Set<UUID> groupIds = request.groupIds() == null ? Set.of() : request.groupIds();
        this.access.requireRoles(roleIds);
        this.groups.requireGroups(groupIds);
        UUID id = this.users.create(
            request.username(),
            request.emails(),
            request.firstName(),
            request.lastName(),
            roleIds
        );
        for (UUID groupId : groupIds) this.groups.addMember(groupId, id);
        return this.responses.created(
            URI.create("/api/v0/users/" + id),
            Map.of("id", id),
            "resource.user.created",
            "User created"
        );
    }

    @Schema(name = "CreateUserRequest")
    record Request(
        @NotBlank @Nullable String username,
        @Nullable Set<@Email @NotBlank String> emails,
        @NotBlank @Nullable String firstName,
        @NotBlank @Nullable String lastName,
        @Nullable Set<UUID> roleIds,
        @Nullable Set<UUID> groupIds
    ) {}

    @Schema(name = "ReplaceUserStatementsRequest")
    record StatementAssignmentRequest(@Nullable Set<UUID> statementIds) {}
}
