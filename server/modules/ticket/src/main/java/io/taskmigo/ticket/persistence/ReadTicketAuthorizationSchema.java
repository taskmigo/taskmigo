package io.taskmigo.ticket.persistence;

import io.taskmigo.eav.query.jpa.EavJpaQueryFieldProvider;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import io.taskmigo.ticket.application.api.TicketAuthorization;
import org.springframework.stereotype.Component;

/// Defines the Object Authorization field surface for reading one Ticket.
@Component
final class ReadTicketAuthorizationSchema extends TicketQuerySchema implements TicketAuthorization.Read {

    ReadTicketAuthorizationSchema(TicketQueryFieldCatalog runtimeFields, EavJpaQueryFieldProvider eavQueryFields) {
        super(runtimeFields, eavQueryFields, false);
    }

    @Override
    protected String operationId() {
        return "ticket.authorization.read";
    }
}
