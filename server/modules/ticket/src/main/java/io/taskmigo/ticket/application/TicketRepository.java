package io.taskmigo.ticket.application;

import io.taskmigo.ticket.domain.Ticket;
import java.util.Optional;
import java.util.UUID;

/// Persists Ticket aggregates without exposing JPA or database details to the application layer.
public interface TicketRepository {
    @SuppressWarnings("NullableProblems")
    void save(Ticket ticket);

    @SuppressWarnings("NullableProblems")
    Optional<Ticket> findById(UUID id);
}
