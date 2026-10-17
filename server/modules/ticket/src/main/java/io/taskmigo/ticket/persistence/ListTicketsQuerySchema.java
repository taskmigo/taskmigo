package io.taskmigo.ticket.persistence;

import io.taskmigo.eav.query.jpa.EavJpaQueryFieldProvider;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import org.springframework.stereotype.Component;

/// Defines the persistence query surface for listing Tickets.
@Component
final class ListTicketsQuerySchema extends TicketQuerySchema {

    ListTicketsQuerySchema(TicketQueryFieldCatalog runtimeFields, EavJpaQueryFieldProvider eavQueryFields) {
        super(runtimeFields, eavQueryFields, true);
    }

    @Override
    protected String operationId() {
        return "tickets.list";
    }
}
