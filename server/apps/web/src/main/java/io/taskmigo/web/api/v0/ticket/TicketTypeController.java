package io.taskmigo.web.api.v0.ticket;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.ticket.type.application.api.TicketFieldSpec;
import io.taskmigo.ticket.type.application.api.TicketTemplateInfo;
import io.taskmigo.ticket.type.application.api.TicketTypeInfo;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import io.taskmigo.ticket.type.domain.TicketTypeStatus;
import io.taskmigo.web.api.v0.support.response.ApiResponse;
import io.taskmigo.web.api.v0.support.response.ApiResponseFactory;
import io.taskmigo.web.api.v0.support.response.ApiV0Responses.OpenApiCommonErrors;
import io.taskmigo.web.api.v0.support.response.ApiV0Responses.OpenApiConflict;
import io.taskmigo.web.api.v0.support.response.ApiV0Responses.OpenApiNotFound;
import io.taskmigo.web.api.v0.support.response.ApiV0Responses.OpenApiUnsupportedMediaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(version = "0", produces = MediaType.APPLICATION_JSON_VALUE)
@OpenApiCommonErrors
@Tag(name = "Ticket Type")
class TicketTypeController {

    private final TicketTypeService types;
    private final ApiResponseFactory responses;

    TicketTypeController(TicketTypeService types, ApiResponseFactory responses) {
        this.types = types;
        this.responses = responses;
    }

    @PostMapping(value = "/ticket-types", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a Ticket Type")
    @OpenApiConflict
    @OpenApiNotFound
    @OpenApiUnsupportedMediaType
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<ApiResponse<TypeResponse, ApiResponse.BasicMeta>> create(
        @Valid @RequestBody CreateTypeRequest request
    ) {
        TicketTypeInfo type = this.types.create(
            Objects.requireNonNull(request.key()),
            Objects.requireNonNull(request.name()),
            specs(request.fields())
        );
        return this.responses.created(
            URI.create("/api/v0/ticket-types/" + type.id()),
            TypeResponse.from(type),
            "resource.ticket-type.created",
            "Ticket Type created"
        );
    }

    @GetMapping("/ticket-types")
    @Operation(summary = "List Ticket Types")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<List<TypeResponse>, ApiResponse.BasicMeta>> list() {
        return this.responses.ok(
            this.types.list().stream().map(TypeResponse::from).toList(),
            "resource.ticket-type.listed",
            "Ticket Types listed"
        );
    }

    @GetMapping("/ticket-types/{ticketTypeId}")
    @Operation(summary = "Get a Ticket Type")
    @ResponseStatus(HttpStatus.OK)
    @OpenApiNotFound
    ResponseEntity<ApiResponse<TypeResponse, ApiResponse.BasicMeta>> get(@PathVariable UUID ticketTypeId) {
        return this.responses.ok(
            TypeResponse.from(this.types.get(ticketTypeId)),
            "resource.ticket-type.read",
            "Ticket Type read"
        );
    }

    @PatchMapping(value = "/ticket-types/{ticketTypeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a Ticket Type")
    @ResponseStatus(HttpStatus.OK)
    @OpenApiNotFound
    @OpenApiUnsupportedMediaType
    ResponseEntity<ApiResponse<TypeResponse, ApiResponse.BasicMeta>> update(
        @PathVariable UUID ticketTypeId,
        @Valid @RequestBody UpdateTypeRequest request
    ) {
        return this.responses.ok(
            TypeResponse.from(this.types.update(ticketTypeId, request.name(), request.status())),
            "resource.ticket-type.updated",
            "Ticket Type updated"
        );
    }

    @PostMapping(value = "/ticket-types/{ticketTypeId}/templates", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create and activate a Ticket template version")
    @OpenApiConflict
    @OpenApiNotFound
    @OpenApiUnsupportedMediaType
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<ApiResponse<TicketTemplateResponse, ApiResponse.BasicMeta>> createTemplate(
        @PathVariable UUID ticketTypeId,
        @Valid @RequestBody CreateTemplateRequest request
    ) {
        TicketTemplateInfo template = this.types.createTemplate(
            ticketTypeId,
            Objects.requireNonNull(request.expectedTemplateVersionId()),
            specs(request.fields())
        );
        return this.responses.created(
            URI.create("/api/v0/ticket-types/" + ticketTypeId + "/templates/" + template.id()),
            TicketTemplateResponse.from(template),
            "resource.ticket-template.created",
            "Ticket template activated"
        );
    }

    @GetMapping("/ticket-types/{ticketTypeId}/templates")
    @Operation(summary = "List Ticket template versions")
    @ResponseStatus(HttpStatus.OK)
    @OpenApiNotFound
    ResponseEntity<ApiResponse<List<TicketTemplateResponse>, ApiResponse.BasicMeta>> listTemplates(
        @PathVariable UUID ticketTypeId
    ) {
        return this.responses.ok(
            this.types.listTemplates(ticketTypeId).stream().map(TicketTemplateResponse::from).toList(),
            "resource.ticket-template.listed",
            "Ticket templates listed"
        );
    }

    @GetMapping("/ticket-types/{ticketTypeId}/templates/{templateVersionId}")
    @Operation(summary = "Get a Ticket template version")
    @ResponseStatus(HttpStatus.OK)
    @OpenApiNotFound
    ResponseEntity<ApiResponse<TicketTemplateResponse, ApiResponse.BasicMeta>> getTemplate(
        @PathVariable UUID ticketTypeId,
        @PathVariable UUID templateVersionId
    ) {
        return this.responses.ok(
            TicketTemplateResponse.from(this.types.getTemplate(ticketTypeId, templateVersionId)),
            "resource.ticket-template.read",
            "Ticket template read"
        );
    }

    private static List<TicketFieldSpec> specs(@Nullable List<FieldRequest> fields) {
        return Objects.requireNonNull(fields).stream().map(FieldRequest::toSpec).toList();
    }

    @Schema(name = "TicketTypeInfo")
    record TypeResponse(
        UUID id,
        String key,
        String name,
        TicketTypeStatus status,
        Instant createdAt,
        Instant updatedAt,
        TicketTemplateResponse currentTemplate
    ) {
        static TypeResponse from(TicketTypeInfo type) {
            return new TypeResponse(
                type.id(),
                type.key(),
                type.name(),
                type.status(),
                type.createdAt(),
                type.updatedAt(),
                TicketTemplateResponse.from(type.currentTemplate())
            );
        }
    }

    @Schema(name = "CreateTicketTypeRequest")
    record CreateTypeRequest(
        @NotBlank @Nullable String key,
        @NotBlank @Nullable String name,
        @NotNull @Nullable List<@NotNull @Valid FieldRequest> fields
    ) {}

    @Schema(name = "UpdateTicketTypeRequest")
    record UpdateTypeRequest(@Nullable String name, @Nullable TicketTypeStatus status) {}

    @Schema(name = "CreateTicketTemplateRequest")
    record CreateTemplateRequest(
        @NotNull @Nullable UUID expectedTemplateVersionId,
        @NotNull @Nullable List<@NotNull @Valid FieldRequest> fields
    ) {}

    @Schema(name = "TicketTemplateFieldRequest")
    record FieldRequest(
        @NotNull @Nullable UUID fieldId,
        @Nullable String label,
        boolean required,
        @Nullable Map<String, Object> constraints
    ) {
        TicketFieldSpec toSpec() {
            return new TicketFieldSpec(
                Objects.requireNonNull(this.fieldId),
                this.label,
                this.required,
                this.constraints == null ? Map.of() : this.constraints
            );
        }
    }
}
