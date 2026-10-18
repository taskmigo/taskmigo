package io.taskmigo.ticket.application.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// Exposes stable Ticket fields together with custom values interpreted by the pinned template version.
public record TicketInfo(
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
    public TicketInfo {
        Objects.requireNonNull(id);
        Objects.requireNonNull(ticketTypeId);
        Objects.requireNonNull(templateVersionId);
        Objects.requireNonNull(title);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdBy);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        customFields = Map.copyOf(Objects.requireNonNull(customFields));
    }
}
