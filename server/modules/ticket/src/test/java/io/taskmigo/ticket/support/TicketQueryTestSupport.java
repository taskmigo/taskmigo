package io.taskmigo.ticket.support;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QuerySchemaView;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

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

    public static List<QuerySchemaView> schemas(String... operations) {
        return Arrays.stream(operations).map(TicketQueryTestSupport::schema).toList();
    }

    private static QuerySchemaView schema(String operation) {
        return new QuerySchemaView() {
            @Override
            public String operation() {
                return operation;
            }

            @Override
            public Collection<QueryFieldDescriptor> fields(QueryFieldContext context) {
                return List.of();
            }
        };
    }
}
