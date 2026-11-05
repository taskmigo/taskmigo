package io.taskmigo.ticket.persistence;

import io.taskmigo.eav.query.jpa.EavJpaQueryFieldProvider;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import io.taskmigo.ticket.application.api.TicketQuery;
import org.springframework.stereotype.Component;

/// Defines the persistence query surface for listing Tickets.
@Component
final class ListTicketsQuerySchema extends TicketQuerySchema implements TicketQuery.List {

    ListTicketsQuerySchema(TicketQueryFieldCatalog runtimeFields, EavJpaQueryFieldProvider eavQueryFields) {
        super(runtimeFields, eavQueryFields, true);
    }

    @Override
    protected String operationId() {
        return "ticket.query.list";
    }
}
