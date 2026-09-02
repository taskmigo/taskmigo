package io.taskmigo.auth.api.v0.group;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.api.v0.ApiV0Controller;
import io.taskmigo.api.v0.infrastructure.pagination.OffsetPageRequest;
import io.taskmigo.api.v0.infrastructure.response.ApiResponse;
import io.taskmigo.api.v0.infrastructure.response.ApiResponseFactory;
import io.taskmigo.auth.api.v0.security.ObjectAuthorizationContext;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationService;
import io.taskmigo.auth.group.GroupInfo;
import io.taskmigo.auth.group.GroupService;
import io.taskmigo.foundation.OffsetPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@ApiV0Controller
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping("/api/v0")
@Tag(name = "Group")
class GroupController {

    private final GroupService groups;
    private final ObjectAuthorizationService objectAuthorization;
    private final ApiResponseFactory responses;

    GroupController(GroupService groups, ObjectAuthorizationService objectAuthorization, ApiResponseFactory responses) {
        this.groups = groups;
        this.objectAuthorization = objectAuthorization;
        this.responses = responses;
    }

    @GetMapping("/groups")
    @Operation(summary = "List groups")
    ResponseEntity<ApiResponse<List<GroupInfo>, ApiResponse.OffsetMeta>> list(
        @ParameterObject @Valid OffsetPageRequest pagination,
        @AuthenticationPrincipal @Nullable Jwt jwt
    ) {
        OffsetPage<GroupInfo> groups = this.groups.list(
            pagination.page(),
            pagination.pageSize(),
            ObjectAuthorizationContext.plan(this.objectAuthorization, jwt, "GET", "/api/v0/groups")
        );
        return this.responses.ok(
            groups.items(),
            new ApiResponse.OffsetPagination(pagination, groups),
            "resource.group.listed",
            "Groups listed"
        );
    }

    @PostMapping("/groups")
    @Operation(summary = "Create a group")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(@Valid @RequestBody Request request) {
        UUID id = this.groups.create(request.name(), request.description(), request.groupIds(), request.roleIds());
        return this.responses.created(
            URI.create("/api/v0/groups/" + id),
            Map.of("id", id),
            "resource.group.created",
            "Group created"
        );
    }

    @Schema(name = "CreateGroupRequest")
    record Request(
        @NotBlank @Nullable String name,
        @Nullable String description,
        @Nullable Set<UUID> groupIds,
        @Nullable Set<UUID> roleIds
    ) {}
}
