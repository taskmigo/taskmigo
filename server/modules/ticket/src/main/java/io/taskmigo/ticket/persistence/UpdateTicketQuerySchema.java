package io.taskmigo.ticket.persistence;

import io.taskmigo.eav.query.jpa.EavJpaQueryFieldProvider;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import org.springframework.stereotype.Component;

/// Defines the persistence query surface for updating one Ticket.
@Component
final class UpdateTicketQuerySchema extends TicketQuerySchema {

    UpdateTicketQuerySchema(TicketQueryFieldCatalog runtimeFields, EavJpaQueryFieldProvider eavQueryFields) {
        super(runtimeFields, eavQueryFields, false);
    }

    @Override
    protected String operationId() {
        return "tickets.update";
    }
}
