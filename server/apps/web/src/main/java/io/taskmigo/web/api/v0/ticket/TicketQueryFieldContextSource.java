package io.taskmigo.web.api.v0.ticket;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.api.TicketAuthorization;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import io.taskmigo.web.api.support.query.QueryFieldContextSource;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

/// Resolves Ticket runtime fields from the HTTP scope required by each Authorization Contract.
@Component
final class TicketQueryFieldContextSource implements QueryFieldContextSource {

    private final TicketQueryFieldContexts fields;

    TicketQueryFieldContextSource(TicketQueryFieldContexts fields) {
        this.fields = Objects.requireNonNull(fields);
    }

    @Override
    public boolean supports(Class<?> contract) {
        return contract == TicketAuthorization.List.class ||
            contract == TicketAuthorization.Read.class ||
            contract == TicketAuthorization.Update.class;
    }

    @Override
    public QueryFieldContext resolve(Class<?> contract, NativeWebRequest request) {
        if (contract == TicketAuthorization.List.class) {
            return this.listContext(request);
        }
        if (contract == TicketAuthorization.Read.class || contract == TicketAuthorization.Update.class) {
            return this.ticketContext(request);
        }
        throw new IllegalArgumentException("Unsupported Ticket authorization contract: " + contract.getName());
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
