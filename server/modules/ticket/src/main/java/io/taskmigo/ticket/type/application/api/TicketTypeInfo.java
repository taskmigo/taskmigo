package io.taskmigo.ticket.type.application.api;

import io.taskmigo.ticket.type.domain.TicketTypeStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/// Exposes stable Ticket Type metadata without leaking persistence details.
public record TicketTypeInfo(
    UUID id,
    String key,
    String name,
    TicketTypeStatus status,
    UUID schemaDefinitionId,
    Instant createdAt,
    Instant updatedAt
) {
    public TicketTypeInfo {
        Objects.requireNonNull(id);
        Objects.requireNonNull(key);
        Objects.requireNonNull(name);
        Objects.requireNonNull(status);
        Objects.requireNonNull(schemaDefinitionId);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }
}
