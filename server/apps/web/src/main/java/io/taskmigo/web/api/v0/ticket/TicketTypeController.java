package io.taskmigo.web.api.v0.ticket;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.eav.schema.application.EavFieldSpec;
import io.taskmigo.eav.schema.application.EavSchemaVersionInfo;
import io.taskmigo.eav.schema.domain.EavDataType;
import io.taskmigo.eav.schema.domain.EavSchemaStatus;
import io.taskmigo.ticket.type.application.api.TicketTypeInfo;
import io.taskmigo.ticket.type.application.api.TicketTypeService;
import io.taskmigo.ticket.type.domain.TicketTypeStatus;
import io.taskmigo.web.api.v0.support.response.ApiResponse;
import io.taskmigo.web.api.v0.support.response.ApiResponseFactory;
import io.taskmigo.web.api.v0.support.response.ApiV0Responses.OpenApiCommonErrors;
import io.taskmigo.web.api.v0.support.response.ApiV0Responses.OpenApiNotFound;
import io.taskmigo.web.api.v0.support.response.ApiV0Responses.OpenApiUnsupportedMediaType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
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
    @OpenApiUnsupportedMediaType
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(
        @Valid @RequestBody CreateTypeRequest request
    ) {
        UUID id = this.types.create(Objects.requireNonNull(request.key()), Objects.requireNonNull(request.name()));
        return this.responses.created(
            URI.create("/api/v0/ticket-types/" + id),
            Map.of("id", id),
            "resource.ticket-type.created",
            "Ticket Type created"
        );
    }

    @GetMapping("/ticket-types")
    @Operation(summary = "List Ticket Types")
    ResponseEntity<ApiResponse<List<TypeResponse>, ApiResponse.BasicMeta>> list() {
        return this.responses.ok(
            this.types.list().stream().map(TypeResponse::from).toList(),
            "resource.ticket-type.listed",
            "Ticket Types listed"
        );
    }

    @GetMapping("/ticket-types/{ticketTypeId}")
    @Operation(summary = "Get a Ticket Type")
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
    @OpenApiNotFound
    @OpenApiUnsupportedMediaType
    ResponseEntity<ApiResponse<TypeResponse, ApiResponse.BasicMeta>> update(
        @PathVariable UUID ticketTypeId,
        @Valid @RequestBody UpdateTypeRequest request
    ) {
        TicketTypeInfo current = this.types.get(ticketTypeId);
        String name = request.name() == null ? current.name() : request.name();
        TicketTypeStatus status = request.status() == null ? current.status() : request.status();
        return this.responses.ok(
            TypeResponse.from(this.types.update(ticketTypeId, name, status)),
            "resource.ticket-type.updated",
            "Ticket Type updated"
        );
    }

    @PostMapping("/ticket-types/{ticketTypeId}/templates")
    @Operation(summary = "Create a Ticket template draft")
    @OpenApiNotFound
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<ApiResponse<TemplateResponse, ApiResponse.BasicMeta>> createTemplate(
        @PathVariable UUID ticketTypeId
    ) {
        EavSchemaVersionInfo template = this.types.createTemplate(ticketTypeId);
        return this.responses.created(
            URI.create("/api/v0/ticket-types/" + ticketTypeId + "/templates/" + template.id()),
            TemplateResponse.from(template),
            "resource.ticket-template.created",
            "Ticket template draft created"
        );
    }

    @GetMapping("/ticket-types/{ticketTypeId}/templates")
    @Operation(summary = "List Ticket template versions")
    @OpenApiNotFound
    ResponseEntity<ApiResponse<List<TemplateResponse>, ApiResponse.BasicMeta>> listTemplates(
        @PathVariable UUID ticketTypeId
    ) {
        return this.responses.ok(
            this.types.listTemplates(ticketTypeId).stream().map(TemplateResponse::from).toList(),
            "resource.ticket-template.listed",
            "Ticket templates listed"
        );
    }

    @GetMapping("/ticket-types/{ticketTypeId}/templates/{templateVersionId}")
    @Operation(summary = "Get a Ticket template version")
    @OpenApiNotFound
    ResponseEntity<ApiResponse<TemplateResponse, ApiResponse.BasicMeta>> getTemplate(
        @PathVariable UUID ticketTypeId,
        @PathVariable UUID templateVersionId
    ) {
        return this.responses.ok(
            TemplateResponse.from(this.types.getTemplate(ticketTypeId, templateVersionId)),
            "resource.ticket-template.read",
            "Ticket template read"
        );
    }

    @PatchMapping(
        value = "/ticket-types/{ticketTypeId}/templates/{templateVersionId}",
        consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Replace fields in a Ticket template draft")
    @OpenApiNotFound
    @OpenApiUnsupportedMediaType
    ResponseEntity<ApiResponse<TemplateResponse, ApiResponse.BasicMeta>> replaceTemplate(
        @PathVariable UUID ticketTypeId,
        @PathVariable UUID templateVersionId,
        @Valid @RequestBody UpdateTemplateRequest request
    ) {
        EavSchemaVersionInfo current = this.types.getTemplate(ticketTypeId, templateVersionId);
        Map<String, EavFieldSpec> existing = new LinkedHashMap<>();
        current.fields().forEach(field -> existing.put(field.key(), field));
        List<EavFieldSpec> fields = Objects.requireNonNull(request.fields())
            .stream()
            .map(field -> field.toSpec(existing))
            .toList();
        return this.responses.ok(
            TemplateResponse.from(this.types.replaceTemplate(ticketTypeId, templateVersionId, fields)),
            "resource.ticket-template.updated",
            "Ticket template updated"
        );
    }

    @PostMapping("/ticket-types/{ticketTypeId}/templates/{templateVersionId}/publish")
    @Operation(summary = "Publish a Ticket template version")
    @OpenApiNotFound
    ResponseEntity<ApiResponse<TemplateResponse, ApiResponse.BasicMeta>> publishTemplate(
        @PathVariable UUID ticketTypeId,
        @PathVariable UUID templateVersionId
    ) {
        return this.responses.ok(
            TemplateResponse.from(this.types.publishTemplate(ticketTypeId, templateVersionId)),
            "resource.ticket-template.published",
            "Ticket template published"
        );
    }

    @PostMapping("/ticket-types/{ticketTypeId}/templates/{templateVersionId}/retire")
    @Operation(summary = "Retire a Ticket template version")
    @OpenApiNotFound
    ResponseEntity<ApiResponse<TemplateResponse, ApiResponse.BasicMeta>> retireTemplate(
        @PathVariable UUID ticketTypeId,
        @PathVariable UUID templateVersionId
    ) {
        return this.responses.ok(
            TemplateResponse.from(this.types.retireTemplate(ticketTypeId, templateVersionId)),
            "resource.ticket-template.retired",
            "Ticket template retired"
        );
    }

    @Schema(name = "TicketTypeInfo")
    record TypeResponse(
        UUID id,
        String key,
        String name,
        TicketTypeStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        static TypeResponse from(TicketTypeInfo type) {
            return new TypeResponse(
                type.id(),
                type.key(),
                type.name(),
                type.status(),
                type.createdAt(),
                type.updatedAt()
            );
        }
    }

    @Schema(name = "TicketTemplateVersion")
    record TemplateResponse(
        UUID id,
        long version,
        EavSchemaStatus status,
        List<FieldResponse> fields,
        @Nullable Instant publishedAt,
        @Nullable Instant retiredAt
    ) {
        static TemplateResponse from(EavSchemaVersionInfo template) {
            return new TemplateResponse(
                template.id(),
                template.version(),
                template.status(),
                template.fields().stream().map(FieldResponse::from).toList(),
                template.publishedAt().orElse(null),
                template.retiredAt().orElse(null)
            );
        }
    }

    @Schema(name = "TicketTemplateField")
    record FieldResponse(
        UUID fieldId,
        String key,
        String label,
        EavDataType dataType,
        boolean required,
        Map<String, Object> constraints
    ) {
        static FieldResponse from(EavFieldSpec field) {
            return new FieldResponse(
                field.fieldId(),
                field.key(),
                field.label(),
                field.dataType(),
                field.required(),
                field.constraints()
            );
        }
    }

    @Schema(name = "CreateTicketTypeRequest")
    record CreateTypeRequest(@NotBlank @Nullable String key, @NotBlank @Nullable String name) {}

    @Schema(name = "UpdateTicketTypeRequest")
    record UpdateTypeRequest(@Nullable String name, @Nullable TicketTypeStatus status) {}

    @Schema(name = "UpdateTicketTemplateRequest")
    record UpdateTemplateRequest(@NotNull @Nullable List<@NotNull @Valid FieldRequest> fields) {}

    @Schema(name = "TicketTemplateFieldRequest")
    record FieldRequest(
        @Nullable UUID fieldId,
        @NotBlank @Nullable String key,
        @NotBlank @Nullable String label,
        @NotNull @Nullable EavDataType dataType,
        boolean required,
        @Nullable Map<String, Object> constraints
    ) {
        EavFieldSpec toSpec(Map<String, EavFieldSpec> existing) {
            String fieldKey = Objects.requireNonNull(this.key);
            String fieldLabel = Objects.requireNonNull(this.label);
            EavDataType fieldType = Objects.requireNonNull(this.dataType);
            UUID identity = this.fieldId;
            EavFieldSpec previous = existing.get(fieldKey);
            if (identity == null && previous != null && previous.dataType() == fieldType) {
                identity = previous.fieldId();
            }
            if (identity == null) {
                identity = UUID.randomUUID();
            }
            return new EavFieldSpec(
                identity,
                fieldKey,
                fieldLabel,
                fieldType,
                this.required,
                this.constraints == null ? Map.of() : this.constraints
            );
        }
    }
}
