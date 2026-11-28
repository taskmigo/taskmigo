package io.taskmigo.web.api.v0.ticket;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.ticket.field.application.api.RegisteredTicketField;
import io.taskmigo.ticket.field.application.api.TicketFieldService;
import io.taskmigo.ticket.field.application.api.TicketFieldStatus;
import io.taskmigo.ticket.field.application.api.TicketFieldType;
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
import java.util.List;
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
@Tag(name = "Ticket Field")
class TicketFieldController {

    private final TicketFieldService fields;
    private final ApiResponseFactory responses;

    TicketFieldController(TicketFieldService fields, ApiResponseFactory responses) {
        this.fields = fields;
        this.responses = responses;
    }

    @PostMapping(value = "/ticket-fields", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Register a shared Ticket field")
    @OpenApiConflict
    @OpenApiUnsupportedMediaType
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<ApiResponse<RegisteredTicketField, ApiResponse.BasicMeta>> create(
        @Valid @RequestBody CreateRequest request
    ) {
        RegisteredTicketField field = this.fields.create(
            Objects.requireNonNull(request.key()),
            Objects.requireNonNull(request.label()),
            Objects.requireNonNull(request.dataType())
        );
        return this.responses.created(
            URI.create("/api/v0/ticket-fields/" + field.id()),
            field,
            "resource.ticket-field.created",
            "Ticket field created"
        );
    }

    @GetMapping("/ticket-fields")
    @Operation(summary = "List registered Ticket fields and query capabilities")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<List<RegisteredTicketField>, ApiResponse.BasicMeta>> list() {
        return this.responses.ok(this.fields.list(), "resource.ticket-field.listed", "Ticket fields listed");
    }

    @GetMapping("/ticket-fields/{fieldId}")
    @Operation(summary = "Get a registered Ticket field")
    @OpenApiNotFound
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<RegisteredTicketField, ApiResponse.BasicMeta>> get(@PathVariable UUID fieldId) {
        return this.responses.ok(this.fields.get(fieldId), "resource.ticket-field.read", "Ticket field read");
    }

    @PatchMapping(value = "/ticket-fields/{fieldId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a Ticket field label or archive it")
    @OpenApiConflict
    @OpenApiNotFound
    @OpenApiUnsupportedMediaType
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<RegisteredTicketField, ApiResponse.BasicMeta>> update(
        @PathVariable UUID fieldId,
        @Valid @RequestBody UpdateRequest request
    ) {
        return this.responses.ok(
            this.fields.update(fieldId, request.label(), request.status()),
            "resource.ticket-field.updated",
            "Ticket field updated"
        );
    }

    @Schema(name = "CreateTicketFieldRequest")
    record CreateRequest(
        @NotBlank @Nullable String key,
        @NotBlank @Nullable String label,
        @NotNull @Nullable TicketFieldType dataType
    ) {}

    @Schema(name = "UpdateTicketFieldRequest")
    record UpdateRequest(@Nullable String label, @Nullable TicketFieldStatus status) {}
}
