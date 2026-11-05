package io.taskmigo.ticket.support;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.ticket.application.api.TicketQuery;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TicketQueryTestSupport {

    private TicketQueryTestSupport() {}

    public static TicketQueryFieldContexts emptyContexts() {
        return new TicketQueryFieldContexts() {
            @Override
            public QueryFieldContext global() {
                return QueryFieldContext.empty();
            }

            @Override
            public QueryFieldContext forType(UUID ticketTypeId) {
                return QueryFieldContext.empty();
            }

            @Override
            public QueryFieldContext forTicket(UUID ticketId) {
                return QueryFieldContext.empty();
            }
        };
    }

    public static TicketQuery.List schemas(String listOperation, String readOperation, String updateOperation) {
        return new TicketQuery.List() {
            @Override
            public String operation() {
                return listOperation;
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return List.of();
            }
        };
    }
}
