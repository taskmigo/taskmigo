package io.taskmigo.web.adapter.in.http.api.v0.auth.authorization;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.statement.ApiInfo;
import io.taskmigo.authorization.statement.Effect;
import io.taskmigo.authorization.statement.Scope;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.authorization.statement.application.port.in.api.StatementService;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.web.adapter.in.http.api.v0.support.pagination.OffsetPageRequest;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiResponse;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiResponseFactory;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiV0Responses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import java.util.Map;
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
@ApiV0Responses.Common
@Tag(name = "Statement")
class StatementController {

    private final StatementService statements;
    private final ApiResponseFactory responses;

    StatementController(StatementService statements, ApiResponseFactory responses) {
        this.statements = statements;
        this.responses = responses;
    }

    @PostMapping(value = "/statements", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an authorization statement")
    @ApiV0Responses.RequestBody
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(@Valid @RequestBody Request request) {
        UUID id = this.statements.create(
            request.code(),
            request.description(),
            Effect.from(request.effect()),
            Scope.from(request.scope()),
            request.target().api().method(),
            request.target().api().path(),
            request.policy()
        );
        return this.responses.created(
            URI.create("/api/v0/statements/" + id),
            Map.of("id", id),
            "resource.statement.created",
            "Statement created"
        );
    }

    @GetMapping("/statements")
    @Operation(summary = "List authorization statements")
    ResponseEntity<ApiResponse<List<Response>, ApiResponse.OffsetMeta>> list(
        @ParameterObject @Valid OffsetPageRequest pagination,
        FilteredQuery<StatementInfo> filter,
        ObjectAuthorizationPredicate<StatementInfo> authorization
    ) {
        OffsetPage<StatementInfo> page = this.statements.list(
            pagination.getPage(),
            pagination.getPageSize(),
            filter.predicate(),
            authorization
        );
        return this.responses.ok(
            page.items().stream().map(Response::from).toList(),
            new ApiResponse.OffsetPagination(pagination, page),
            "resource.statement.listed",
            "Statements listed"
        );
    }

    @Schema(name = "StatementInfo")
    record Response(
        UUID id,
        String code,
        @Nullable String description,
        Effect effect,
        Scope scope,
        TargetResponse target,
        String policy
    ) {
        static Response from(StatementInfo statement) {
            ApiInfo apiTarget = statement.target().api();
            return new Response(
                statement.id(),
                statement.code(),
                statement.description(),
                statement.effect(),
                statement.scope(),
                new TargetResponse(new ApiResponseData(apiTarget.method(), apiTarget.path())),
                statement.policy()
            );
        }
    }

    record TargetResponse(ApiResponseData api) {}

    record ApiResponseData(String method, String path) {}

    @Schema(name = "CreateStatementRequest")
    record Request(
        @NotBlank @Nullable String code,
        @Nullable String description,
        @NotBlank @Pattern(regexp = "allow|deny", message = "must be exactly allow or deny") String effect,
        @NotBlank @Pattern(regexp = "request|object", message = "must be exactly request or object") String scope,
        @Valid @NotNull Target target,
        @NotBlank String policy
    ) {}

    record Target(@NotNull ApiInfo api) {}
}
