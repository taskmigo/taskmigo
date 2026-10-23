package io.taskmigo.ticket.type.application.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/// Exposes one resolved custom field in a Ticket template revision.
public record TicketFieldInfo(
    UUID fieldId,
    String key,
    String label,
    TicketFieldType dataType,
    boolean required,
    Map<String, Object> constraints
) {
    public TicketFieldInfo {
        Objects.requireNonNull(fieldId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(label);
        Objects.requireNonNull(dataType);
        constraints = Map.copyOf(Objects.requireNonNull(constraints));
    }
}
