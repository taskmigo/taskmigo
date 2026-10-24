package io.taskmigo.ticket.application;

import io.taskmigo.query.QuerySchemaView;

/// Provides the Ticket list query schema required by query compilation.
public interface TicketQuerySchemas {
    QuerySchemaView list();
}
