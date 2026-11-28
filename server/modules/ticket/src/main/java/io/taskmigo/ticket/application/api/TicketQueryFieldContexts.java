package io.taskmigo.ticket.application.api;

import io.taskmigo.query.QueryFieldContext;

/// Supplies a request-scoped snapshot of the shared Ticket field registry.
public interface TicketQueryFieldContexts {
    /// Captures the same field namespace for listing, reading and updating Tickets.
    QueryFieldContext snapshot();
}
