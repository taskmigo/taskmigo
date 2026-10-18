package io.taskmigo.ticket.application.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Represents partial Ticket mutation; custom-field null values remove optional EAV values.
public record TicketPatch(@Nullable String title, @Nullable String status, Map<String, ?> customFields) {
    public TicketPatch {
        Objects.requireNonNull(customFields);
        LinkedHashMap<String, @Nullable Object> copy = new LinkedHashMap<>();
        customFields.forEach((key, value) -> copy.put(Objects.requireNonNull(key), value));
        customFields = Collections.unmodifiableMap(copy);
    }

    /// Returns whether the patch requests any common or custom-field mutation.
    public boolean hasChanges() {
        return this.title != null || this.status != null || !this.customFields.isEmpty();
    }
}
