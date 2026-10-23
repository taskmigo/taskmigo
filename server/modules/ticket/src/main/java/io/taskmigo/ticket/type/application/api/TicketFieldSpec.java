package io.taskmigo.ticket.type.application.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Describes one custom field requested for a Ticket template.
///
/// When `fieldId` is absent, the Ticket application preserves the existing field identity for the
/// same key and type when possible; otherwise it allocates a new identity.
public record TicketFieldSpec(
    @Nullable UUID fieldId,
    String key,
    String label,
    TicketFieldType dataType,
    boolean required,
    Map<String, Object> constraints
) {
    public TicketFieldSpec {
        Objects.requireNonNull(key);
        Objects.requireNonNull(label);
        Objects.requireNonNull(dataType);
        constraints = Map.copyOf(Objects.requireNonNull(constraints));
    }
}
