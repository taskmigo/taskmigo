package io.taskmigo.ticket.application;

import io.taskmigo.query.QuerySchemaView;

/// Provides the operation-scoped Ticket query schemas required by Ticket use cases.
public interface TicketQuerySchemas {
    QuerySchemaView list();
    QuerySchemaView read();
    QuerySchemaView update();
}
