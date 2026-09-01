package io.taskmigo.web.api.v0.feature.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.access.AccessService;
import io.taskmigo.group.GroupService;
import io.taskmigo.user.UserService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
    private final ApiResponseFactory responses;

    UserController(UserService users, AccessService access, GroupService groups, ApiResponseFactory responses) {
        this.users = users;
        this.access = access;
        this.groups = groups;
        this.responses = responses;
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
}
