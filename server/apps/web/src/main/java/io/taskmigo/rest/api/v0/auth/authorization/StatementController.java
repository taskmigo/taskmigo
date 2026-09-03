package io.taskmigo.rest.api.v0.auth.authorization;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.auth.authorization.object.ObjectAuthorizationService;
import io.taskmigo.auth.authorization.statement.ApiInfo;
import io.taskmigo.auth.authorization.statement.Effect;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import io.taskmigo.auth.authorization.statement.StatementService;
import io.taskmigo.auth.authorization.statement.TargetType;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.rest.api.v0.support.objectauthorization.ObjectAuthorizationContext;
import io.taskmigo.rest.api.v0.support.pagination.OffsetPageRequest;
import io.taskmigo.rest.api.v0.support.response.ApiResponse;
import io.taskmigo.rest.api.v0.support.response.ApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(version = "0")
@Tag(name = "Statement")
class StatementController {

    private final StatementService statements;
    private final ObjectAuthorizationService objectAuthorization;
    private final ApiResponseFactory responses;

    StatementController(
        StatementService statements,
        ObjectAuthorizationService objectAuthorization,
        ApiResponseFactory responses
    ) {
        this.statements = statements;
        this.objectAuthorization = objectAuthorization;
        this.responses = responses;
    }

    @PostMapping("/statements")
    @Operation(summary = "Create an authorization statement")
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(@Valid @RequestBody Request request) {
        UUID id = this.statements.create(
            request.name(),
            request.description(),
            request.effect(),
            request.target().type(),
            request.target().api().method(),
            request.target().api().path(),
            request.conditions()
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
    ResponseEntity<ApiResponse<List<StatementInfo>, ApiResponse.OffsetMeta>> list(
        @ParameterObject @Valid OffsetPageRequest pagination,
        @AuthenticationPrincipal @Nullable Jwt jwt
    ) {
        OffsetPage<StatementInfo> page = this.statements.list(
            pagination.page(),
            pagination.pageSize(),
            ObjectAuthorizationContext.plan(this.objectAuthorization, jwt, "GET", "/api/v0/statements")
        );
        return this.responses.ok(
            page.items(),
            new ApiResponse.OffsetPagination(pagination, page),
            "resource.statement.listed",
            "Statements listed"
        );
    }

    @Schema(name = "CreateStatementRequest")
    record Request(
        @NotBlank @Nullable String name,
        @Nullable String description,
        @NotNull Effect effect,
        @NotNull Target target,
        @Nullable List<String> conditions
    ) {}

    record Target(@NotNull TargetType type, @NotNull ApiInfo api) {}
}
