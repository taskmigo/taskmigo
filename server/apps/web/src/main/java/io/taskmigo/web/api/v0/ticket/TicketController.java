package io.taskmigo.web.api.v0.ticket;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryOperation;
import io.taskmigo.ticket.application.api.TicketInfo;
import io.taskmigo.ticket.application.api.TicketPatch;
import io.taskmigo.ticket.application.api.TicketService;
import io.taskmigo.web.api.support.objectauthorization.ObjectAuthorizationResolver;
import io.taskmigo.web.api.support.objectauthorization.ResolvedObjectAuthorization;
import io.taskmigo.web.api.support.query.ListQueryOptions;
import io.taskmigo.web.api.v0.support.pagination.OffsetPageRequest;
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
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(version = "0", produces = MediaType.APPLICATION_JSON_VALUE)
@OpenApiCommonErrors
@Tag(name = "Ticket")
class TicketController {

    private final TicketService tickets;
    private final ObjectAuthorizationResolver objectAuthorization;
    private final ApiResponseFactory responses;

    TicketController(
        TicketService tickets,
        ObjectAuthorizationResolver objectAuthorization,
        ApiResponseFactory responses
    ) {
        this.tickets = tickets;
        this.objectAuthorization = objectAuthorization;
        this.responses = responses;
    }

    @PostMapping(value = "/tickets", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a Ticket")
    @OpenApiUnsupportedMediaType
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<ApiResponse<Map<String, UUID>, ApiResponse.BasicMeta>> create(
        @Valid @RequestBody CreateRequest request,
        JwtAuthenticationToken authentication
    ) {
        UUID id = this.tickets.create(
            request.ticketTypeId(),
            request.title(),
            request.status(),
            createFields(request.customFields()),
            actorId(authentication)
        );
        return this.responses.created(
            URI.create("/api/v0/tickets/" + id),
            Map.of("id", id),
            "resource.ticket.created",
            "Ticket created"
        );
    }

    @GetMapping("/tickets")
    @QueryOperation("tickets.list")
    @Operation(summary = "List Tickets")
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<List<Response>, ApiResponse.OffsetMeta>> list(
        @ParameterObject @Valid OffsetPageRequest pagination,
        @RequestParam(required = false) @Nullable UUID ticketTypeId,
        @ParameterObject @Valid ListQueryOptions query
    ) {
        ResolvedObjectAuthorization<TicketInfo> authorization = this.objectAuthorization.resolve();
        OffsetPage<TicketInfo> page = this.tickets.list(
            pagination.getPage(),
            pagination.getPageSize(),
            ticketTypeId,
            query.toRequest(),
            authorization.predicate()
        );
        return this.responses.ok(
            page.items().stream().map(Response::from).toList(),
            new ApiResponse.OffsetPagination(pagination, page),
            "resource.ticket.listed",
            "Tickets listed"
        );
    }

    @GetMapping("/tickets/{ticketId}")
    @QueryOperation("tickets.read")
    @Operation(summary = "Get a Ticket")
    @OpenApiNotFound
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<Response, ApiResponse.BasicMeta>> read(@PathVariable UUID ticketId) {
        ResolvedObjectAuthorization<TicketInfo> authorization = this.objectAuthorization.resolve();
        return this.responses.ok(
            Response.from(this.tickets.read(ticketId, authorization.predicate())),
            "resource.ticket.read",
            "Ticket read"
        );
    }

    @PatchMapping(value = "/tickets/{ticketId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @QueryOperation("tickets.update")
    @Operation(summary = "Update a Ticket")
    @OpenApiNotFound
    @OpenApiUnsupportedMediaType
    @ResponseStatus(HttpStatus.OK)
    ResponseEntity<ApiResponse<Response, ApiResponse.BasicMeta>> update(
        @PathVariable UUID ticketId,
        @Valid @RequestBody UpdateRequest request
    ) {
        ResolvedObjectAuthorization<TicketInfo> authorization = this.objectAuthorization.resolve();
        Map<String, @Nullable Object> customFields = request.customFields() == null ? Map.of() : request.customFields();
        TicketInfo updated = this.tickets.update(
            ticketId,
            new TicketPatch(request.title(), request.status(), customFields),
            authorization.predicate()
        );
        return this.responses.ok(Response.from(updated), "resource.ticket.updated", "Ticket updated");
    }

    private static Map<String, Object> createFields(@Nullable Map<String, @Nullable Object> supplied) {
        if (supplied == null || supplied.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        supplied.forEach((key, value) -> {
            Objects.requireNonNull(key);
            if (value != null) {
                values.put(key, value);
            }
        });
        return Map.copyOf(values);
    }

    private static UUID actorId(JwtAuthenticationToken authentication) {
        String userId = Objects.requireNonNull(authentication.getToken().getClaimAsString("user_id"));
        return UUID.fromString(userId);
    }

    @Schema(name = "TicketInfo")
    record Response(
        UUID id,
        UUID ticketTypeId,
        UUID templateVersionId,
        String title,
        String status,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        Map<String, Object> customFields
    ) {
        static Response from(TicketInfo ticket) {
            return new Response(
                ticket.id(),
                ticket.ticketTypeId(),
                ticket.templateVersionId(),
                ticket.title(),
                ticket.status(),
                ticket.createdBy(),
                ticket.createdAt(),
                ticket.updatedAt(),
                ticket.customFields()
            );
        }
    }

    @Schema(name = "CreateTicketRequest")
    record CreateRequest(
        @NotNull UUID ticketTypeId,
        @NotBlank String title,
        @NotBlank String status,
        @Nullable Map<String, @Nullable Object> customFields
    ) {}

    @Schema(name = "UpdateTicketRequest")
    record UpdateRequest(
        @Nullable String title,
        @Nullable String status,
        @Nullable Map<String, @Nullable Object> customFields
    ) {}
}
