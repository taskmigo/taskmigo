package io.taskmigo.ticket.persistence;

import io.taskmigo.eav.query.jpa.EavJpaQueryFieldProvider;
import io.taskmigo.ticket.application.TicketQueryFieldCatalog;
import io.taskmigo.ticket.application.api.TicketAuthorization;
import org.springframework.stereotype.Component;

/// Defines the Object Authorization field surface for listing Tickets.
@Component
final class ListTicketsAuthorizationSchema extends TicketQuerySchema implements TicketAuthorization.List {

    ListTicketsAuthorizationSchema(TicketQueryFieldCatalog runtimeFields, EavJpaQueryFieldProvider eavQueryFields) {
        super(runtimeFields, eavQueryFields, false);
    }

    @Override
    protected String operationId() {
        return "ticket.authorization.list";
    }
}
