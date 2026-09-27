package io.taskmigo.web.adapter.in.http.api.v0.audit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.audit.application.port.in.api.AuditLogService;
import io.taskmigo.audit.model.AuditFieldChange;
import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.web.adapter.in.http.api.v0.support.pagination.OffsetPageRequest;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiResponse;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiResponseFactory;
import io.taskmigo.web.adapter.in.http.api.v0.support.response.ApiV0Responses.OpenApiCommonErrors;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(version = "0", produces = MediaType.APPLICATION_JSON_VALUE)
@OpenApiCommonErrors
@Tag(name = "Audit")
class AuditController {

    private final AuditLogService auditLogs;
    private final ApiResponseFactory responses;

    AuditController(AuditLogService auditLogs, ApiResponseFactory responses) {
        this.auditLogs = auditLogs;
        this.responses = responses;
    }

    @GetMapping("/audit/{entityType}/logs")
    @Operation(summary = "List entity audit logs")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<List<Response>, ApiResponse.OffsetMeta>> list(
        @PathVariable String entityType,
        @ParameterObject @Valid OffsetPageRequest pagination
    ) {
        OffsetPage<AuditLog> logs = this.auditLogs.list(entityType, pagination.getPage(), pagination.getPageSize());
        return this.responses.ok(
            logs.items().stream().map(Response::from).toList(),
            new ApiResponse.OffsetPagination(pagination, logs),
            "resource.audit.listed",
            "Audit logs listed"
        );
    }

    @Schema(name = "AuditLog")
    record Response(
        UUID id,
        UUID sourceEventId,
        String entityType,
        UUID entityId,
        ActorResponse actor,
        Instant occurredAt,
        List<ChangeResponse> changes
    ) {
        static Response from(AuditLog log) {
            return new Response(
                log.id(),
                log.sourceEventId(),
                log.entityType(),
                log.entityId(),
                new ActorResponse(log.actor().id(), log.actor().username()),
                log.occurredAt(),
                log.changes().stream().map(ChangeResponse::from).toList()
            );
        }
    }

    @Schema(name = "AuditActor")
    record ActorResponse(UUID id, String username) {}

    @Schema(name = "AuditFieldChange")
    record ChangeResponse(String field, @Nullable Object before, @Nullable Object after, boolean sensitive) {
        static ChangeResponse from(AuditFieldChange change) {
            return new ChangeResponse(change.field(), change.before(), change.after(), change.sensitive());
        }
    }
}
