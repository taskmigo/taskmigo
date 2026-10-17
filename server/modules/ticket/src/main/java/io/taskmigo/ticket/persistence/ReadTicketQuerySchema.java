package io.taskmigo.ticket.persistence;

import io.taskmigo.eav.query.jpa.EavJpaQueryFieldProvider;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import org.springframework.stereotype.Component;

/// Defines the persistence query surface for reading one Ticket.
@Component
final class ReadTicketQuerySchema extends TicketQuerySchema {

    ReadTicketQuerySchema(TicketQueryFieldCatalog runtimeFields, EavJpaQueryFieldProvider eavQueryFields) {
        super(runtimeFields, eavQueryFields, false);
    }

    @Override
    protected String operationId() {
        return "tickets.read";
    }
}
