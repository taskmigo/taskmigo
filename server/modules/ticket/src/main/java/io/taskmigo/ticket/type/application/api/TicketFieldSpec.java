package io.taskmigo.ticket.type.application.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// References a registered field with template-local validation and an optional display label override.
public record TicketFieldSpec(UUID fieldId, @Nullable String label, boolean required, Map<String, Object> constraints) {
    public TicketFieldSpec {
        Objects.requireNonNull(fieldId);
        constraints = Map.copyOf(Objects.requireNonNull(constraints));
    }
}
