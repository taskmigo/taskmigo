package io.taskmigo.ticket.field.application.api;

import io.taskmigo.query.QueryOperator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/// Exposes stable field identity, lifecycle and supported query capabilities.
public record RegisteredTicketField(
    UUID id,
    String key,
    String label,
    TicketFieldType dataType,
    TicketFieldStatus status,
    List<QueryOperator> operators,
    boolean sortable,
    boolean nullable
) {
    public RegisteredTicketField {
        Objects.requireNonNull(id);
        Objects.requireNonNull(key);
        Objects.requireNonNull(label);
        Objects.requireNonNull(dataType);
        Objects.requireNonNull(status);
        operators = List.copyOf(Objects.requireNonNull(operators));
    }
}
