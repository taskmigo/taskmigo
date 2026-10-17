package io.taskmigo.ticket.application.api;

import io.taskmigo.query.QueryFieldContext;
import java.util.UUID;

/// Resolves server-owned runtime query-field snapshots for Ticket operations.
public interface TicketQueryFieldContexts {
    /// Returns the request-independent globally compatible runtime field snapshot.
    QueryFieldContext global();

    /// Returns the runtime field snapshot scoped to one Ticket Type's compatible history.
    QueryFieldContext forType(UUID ticketTypeId);

    /// Returns the exact historical runtime field snapshot for a Ticket, or the safe global snapshot when absent.
    QueryFieldContext forTicket(UUID ticketId);
}
