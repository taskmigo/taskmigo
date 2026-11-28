package io.taskmigo.web.api.v0.ticket;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import io.taskmigo.web.api.support.query.QueryFieldContextSource;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.NativeWebRequest;

/// Captures the shared Ticket query namespace independently of the requested Ticket or Type.
@Component
final class TicketQueryFieldContextSource implements QueryFieldContextSource {

    private static final Set<String> OPERATIONS = Set.of("tickets.list", "tickets.read", "tickets.update");

    private final TicketQueryFieldContexts fields;

    TicketQueryFieldContextSource(TicketQueryFieldContexts fields) {
        this.fields = Objects.requireNonNull(fields);
    }

    @Override
    public boolean supports(String operation) {
        return OPERATIONS.contains(operation);
    }

    @Override
    public QueryFieldContext resolve(String operation, NativeWebRequest request) {
        if (!this.supports(operation)) {
            throw new IllegalArgumentException("Unsupported Ticket query operation: " + operation);
        }
        return this.fields.snapshot();
    }
}
