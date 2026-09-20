package io.taskmigo.rest.api.v0.auth.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.identity.group.application.port.in.api.GroupService;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.rest.api.v0.support.pagination.OffsetPageRequest;
import io.taskmigo.rest.api.v0.support.response.ApiResponse;
import io.taskmigo.rest.api.v0.support.response.ApiResponseFactory;
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
@RequestMapping(version = "0")
@Tag(name = "Group")
class GroupController {

    private final GroupService groups;
    private final ApiResponseFactory responses;

    GroupController(GroupService groups, ApiResponseFactory responses) {
        this.groups = groups;
        this.responses = responses;
    }

    @GetMapping("/groups")
    @Operation(summary = "List groups")
    ResponseEntity<ApiResponse<List<Response>, ApiResponse.OffsetMeta>> list(
        @ParameterObject @Valid OffsetPageRequest pagination,
        FilteredQuery<GroupInfo> filter,
        ObjectAuthorizationPredicate<GroupInfo> authorization
    ) {
        OffsetPage<GroupInfo> groups = this.groups.list(
            pagination.getPage(),
            pagination.getPageSize(),
            filter.predicate(),
            authorization
        );
        return this.responses.ok(
            groups.items().stream().map(Response::from).toList(),
            new ApiResponse.OffsetPagination(pagination, groups),
            "resource.group.listed",
            "Groups listed"
        );
    }

    @PostMapping("/groups")
    @Operation(summary = "Create a group")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(@Valid @RequestBody Request request) {
        UUID id = this.groups.create(
            request.code(),
            request.displayName(),
            request.description(),
            request.groupIds(),
            request.roleIds()
        );
        return this.responses.created(
            URI.create("/api/v0/groups/" + id),
            Map.of("id", id),
            "resource.group.created",
            "Group created"
        );
    }

    @Schema(name = "GroupInfo")
    record Response(
        UUID id,
        String code,
        String displayName,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String description,
        List<Response> children
    ) {
        static Response from(GroupInfo group) {
            return new Response(
                group.id(),
                group.code(),
                group.displayName(),
                group.description(),
                group.children().stream().map(Response::from).toList()
            );
        }
    }

    @Schema(name = "CreateGroupRequest")
    record Request(
        @NotBlank @Nullable String code,
        @NotBlank @Nullable String displayName,
        @Nullable String description,
        @Nullable Set<UUID> groupIds,
        @Nullable Set<UUID> roleIds
    ) {}
}
