package io.taskmigo.web.api.v0.auth.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.identity.group.application.api.GroupService;
import io.taskmigo.query.ListQuerySpec;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.web.api.support.objectauthorization.ObjectAuthorizationResolver;
import io.taskmigo.web.api.support.objectauthorization.ResolvedObjectAuthorization;
import io.taskmigo.web.api.support.query.ListQueryOptions;
import io.taskmigo.web.api.support.query.ListQueryResolver;
import io.taskmigo.web.api.v0.support.pagination.OffsetPageRequest;
import io.taskmigo.web.api.v0.support.response.ApiResponse;
import io.taskmigo.web.api.v0.support.response.ApiResponseFactory;
import io.taskmigo.web.api.v0.support.response.ApiV0Responses.OpenApiCommonErrors;
import io.taskmigo.web.api.v0.support.response.ApiV0Responses.OpenApiConflict;
import io.taskmigo.web.api.v0.support.response.ApiV0Responses.OpenApiUnsupportedMediaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(version = "0", produces = MediaType.APPLICATION_JSON_VALUE)
@OpenApiCommonErrors
@Tag(name = "Group")
class GroupController {

    private final GroupService groups;
    private final ListQueryResolver queryResolver;
    private final ObjectAuthorizationResolver objectAuthorization;
    private final ApiResponseFactory responses;

    GroupController(
        GroupService groups,
        ListQueryResolver queryResolver,
        ObjectAuthorizationResolver objectAuthorization,
        ApiResponseFactory responses
    ) {
        this.groups = groups;
        this.queryResolver = queryResolver;
        this.objectAuthorization = objectAuthorization;
        this.responses = responses;
    }

    @GetMapping("/groups")
    @QueryOperation("identity.groups.list")
    @Operation(summary = "List groups")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<List<Response>, ApiResponse.OffsetMeta>> list(
        @ParameterObject @Valid OffsetPageRequest pagination,
        @ParameterObject @Valid ListQueryOptions query
    ) {
        ListQuerySpec<GroupInfo> filter = this.queryResolver.resolve(query);
        ResolvedObjectAuthorization<GroupInfo> authorization = this.objectAuthorization.resolve();
        OffsetPage<GroupInfo> groups = this.groups.list(
            pagination.getPage(),
            pagination.getPageSize(),
            filter,
            authorization.predicate()
        );
        return this.responses.ok(
            groups.items().stream().map(Response::from).toList(),
            new ApiResponse.OffsetPagination(pagination, groups),
            "resource.group.listed",
            "Groups listed"
        );
    }

    @PostMapping(value = "/groups", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a group")
    @OpenApiConflict
    @OpenApiUnsupportedMediaType
    @ResponseStatus(HttpStatus.CREATED)
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
        @Nullable Set<@NotNull UUID> groupIds,
        @Nullable Set<@NotNull UUID> roleIds
    ) {}
}
