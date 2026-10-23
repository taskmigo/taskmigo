package io.taskmigo.ticket.type.application.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/// Exposes one Ticket template revision without revealing its backing storage model.
public record TicketTemplateInfo(
    UUID id,
    long version,
    TicketTemplateStatus status,
    List<TicketFieldInfo> fields,
    Optional<Instant> publishedAt,
    Optional<Instant> retiredAt
) {
    public TicketTemplateInfo {
        Objects.requireNonNull(id);
        Objects.requireNonNull(status);
        if (version < 1) {
            throw new IllegalArgumentException("Ticket template version must be positive");
        }
        fields = List.copyOf(Objects.requireNonNull(fields));
        Objects.requireNonNull(publishedAt);
        Objects.requireNonNull(retiredAt);
    }
}
