package io.taskmigo.ticket.persistence;

import io.taskmigo.eav.query.jpa.EavJpaQueryFieldProvider;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import io.taskmigo.ticket.application.api.TicketAuthorization;
import org.springframework.stereotype.Component;

/// Defines the Object Authorization field surface for updating one Ticket.
@Component
final class UpdateTicketAuthorizationSchema extends TicketQuerySchema implements TicketAuthorization.Update {

    UpdateTicketAuthorizationSchema(TicketQueryFieldCatalog runtimeFields, EavJpaQueryFieldProvider eavQueryFields) {
        super(runtimeFields, eavQueryFields, false);
    }

    @Override
    protected String operationId() {
        return "ticket.authorization.update";
    }
}
