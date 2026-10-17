package io.taskmigo.ticket.type.application;

import io.taskmigo.ticket.type.domain.TicketType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Persists Ticket Type aggregates without exposing storage mechanics to application services.
public interface TicketTypeRepository {
    @SuppressWarnings("NullableProblems")
    void save(TicketType ticketType);

    @SuppressWarnings("NullableProblems")
    Optional<TicketType> findById(UUID id);

    @SuppressWarnings("NullableProblems")
    boolean existsByKey(String key);

    List<TicketType> findAll();
}
