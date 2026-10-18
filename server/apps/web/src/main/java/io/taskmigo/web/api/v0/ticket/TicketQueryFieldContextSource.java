package io.taskmigo.web.api.v0.ticket;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import io.taskmigo.web.api.support.query.QueryFieldContextSource;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/// Resolves Ticket runtime query fields from server-owned type or historical Ticket scope.
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
        return switch (operation) {
            case "tickets.list" -> this.listContext(request);
            case "tickets.read", "tickets.update" -> this.ticketContext(request);
            default -> throw new IllegalArgumentException("Unsupported Ticket query operation: " + operation);
        };
    }

    private QueryFieldContext listContext(NativeWebRequest request) {
        String rawTypeId = request.getParameter("ticketTypeId");
        if (rawTypeId == null || rawTypeId.isBlank()) {
            return this.fields.global();
        }
        return this.fields.forType(UUID.fromString(rawTypeId));
    }

    private QueryFieldContext ticketContext(NativeWebRequest request) {
        Object rawVariables = request.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST
        );
        if (!(rawVariables instanceof Map<?, ?> variables)) {
            return this.fields.global();
        }
        Object rawTicketId = variables.get("ticketId");
        if (!(rawTicketId instanceof String value) || value.isBlank()) {
            return this.fields.global();
        }
        return this.fields.forTicket(UUID.fromString(value));
    }
}
