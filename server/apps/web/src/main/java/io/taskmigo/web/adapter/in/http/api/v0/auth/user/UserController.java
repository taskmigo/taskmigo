package io.taskmigo.web.adapter.in.http.api.v0.auth.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.user.UserInfo;
import io.taskmigo.identity.user.application.port.in.api.UserRegistrationService;
import io.taskmigo.identity.user.application.port.in.api.UserService;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.web.adapter.in.http.api.v0.support.pagination.OffsetPageRequest;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiResponse;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(version = "0")
@Tag(name = "User")
class UserController {

    private final UserService users;
    private final UserRegistrationService registrations;
    private final ApiResponseFactory responses;

    UserController(UserService users, UserRegistrationService registrations, ApiResponseFactory responses) {
        this.users = users;
        this.registrations = registrations;
        this.responses = responses;
    }

    @GetMapping("/users")
    @Operation(summary = "List users")
    ResponseEntity<ApiResponse<List<Response>, ApiResponse.OffsetMeta>> list(
        @ParameterObject @Valid OffsetPageRequest pagination,
        FilteredQuery<UserInfo> filter,
        ObjectAuthorizationPredicate<UserInfo> authorization
    ) {
        OffsetPage<UserInfo> users = this.users.list(
            pagination.getPage(),
            pagination.getPageSize(),
            filter.predicate(),
            authorization
        );
        return this.responses.ok(
            users.items().stream().map(Response::from).toList(),
            new ApiResponse.OffsetPagination(pagination, users),
            "resource.user.listed",
            "Users listed"
        );
    }

    @PatchMapping("/users/{userId}/statements")
    @Operation(summary = "Replace a user's direct statements")
    ResponseEntity<ApiResponse<Void, ApiResponse.BasicMeta>> setStatements(
        @PathVariable UUID userId,
        @Valid @RequestBody StatementAssignmentRequest request
    ) {
        this.users.setStatements(userId, request.statementIds() == null ? Set.of() : request.statementIds());
        return this.responses.ok("resource.user.statements.updated", "User statements updated");
    }

    @PostMapping("/users")
    @Operation(summary = "Create a new user")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(@Valid @RequestBody Request request) {
        UUID id = this.registrations.register(
            request.username(),
            request.emails(),
            request.firstName(),
            request.lastName(),
            request.roleIds(),
            request.groupIds()
        );
        return this.responses.created(
            URI.create("/api/v0/users/" + id),
            Map.of("id", id),
            "resource.user.created",
            "User created"
        );
    }

    @Schema(name = "UserInfo")
    record Response(
        UUID id,
        String username,
        String firstName,
        String lastName,
        Set<String> emails,
        String displayName
    ) {
        static Response from(UserInfo user) {
            return new Response(
                user.id(),
                user.username(),
                user.firstName(),
                user.lastName(),
                user.emails(),
                user.displayName()
            );
        }
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
