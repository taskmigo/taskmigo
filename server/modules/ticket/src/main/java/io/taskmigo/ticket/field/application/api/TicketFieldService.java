package io.taskmigo.ticket.field.application.api;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines the management operations for shared Ticket fields.
public interface TicketFieldService {
    /// Registers a unique key and immutable data type.
    RegisteredTicketField create(String key, String label, TicketFieldType dataType);

    RegisteredTicketField get(UUID id);

    List<RegisteredTicketField> list();

    /// Updates the default label or archives a field without changing its identity.
    RegisteredTicketField update(UUID id, @Nullable String label, @Nullable TicketFieldStatus status);
}
