package io.taskmigo.web.api.v0.ticket;

import io.swagger.v3.oas.annotations.media.Schema;
import io.taskmigo.ticket.field.application.api.TicketFieldType;
import io.taskmigo.ticket.type.application.api.TicketFieldInfo;
import io.taskmigo.ticket.type.application.api.TicketTemplateInfo;
import io.taskmigo.ticket.type.application.api.TicketTemplateStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Shared HTTP representation of an immutable Ticket template snapshot.
@Schema(name = "TicketTemplateVersion")
record TicketTemplateResponse(
    UUID id,
    long version,
    TicketTemplateStatus status,
    List<FieldResponse> fields,
    Instant activatedAt,
    @Nullable Instant supersededAt
) {
    static TicketTemplateResponse from(TicketTemplateInfo template) {
        return new TicketTemplateResponse(
            template.id(),
            template.version(),
            template.status(),
            template.fields().stream().map(FieldResponse::from).toList(),
            template.activatedAt(),
            template.supersededAt().orElse(null)
        );
    }

    @Schema(name = "TicketTemplateField")
    record FieldResponse(
        UUID fieldId,
        String key,
        String label,
        TicketFieldType dataType,
        boolean required,
        Map<String, Object> constraints
    ) {
        static FieldResponse from(TicketFieldInfo field) {
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
}
