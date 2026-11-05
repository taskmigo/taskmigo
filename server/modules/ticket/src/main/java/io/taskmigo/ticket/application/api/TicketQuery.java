package io.taskmigo.ticket.application.api;

import io.taskmigo.query.QuerySchemaView;

/// Typed Query contracts published by the Ticket feature.
public final class TicketQuery {

    private TicketQuery() {}

    public interface List extends QuerySchemaView {}
}
