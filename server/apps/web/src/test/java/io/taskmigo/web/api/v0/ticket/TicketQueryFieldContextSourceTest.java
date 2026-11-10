package io.taskmigo.web.api.v0.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.api.TicketAuthorization;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.HandlerMapping;

class TicketQueryFieldContextSourceTest {

    @Test
    @DisplayName("list context uses explicit Ticket Type scope and otherwise uses the global catalog")
    void shouldResolveListScope() {
        TicketQueryFieldContexts fields = mock(TicketQueryFieldContexts.class);
        TicketQueryFieldContextSource source = new TicketQueryFieldContextSource(fields);
        UUID typeId = UUID.randomUUID();
        QueryFieldContext scoped = new QueryFieldContext(Map.of("scope", "type"));
        QueryFieldContext global = new QueryFieldContext(Map.of("scope", "global"));
        when(fields.forType(typeId)).thenReturn(scoped);
        when(fields.global()).thenReturn(global);
        MockHttpServletRequest scopedRequest = new MockHttpServletRequest();
        scopedRequest.setParameter("ticketTypeId", typeId.toString());

        assertThat(source.resolve(TicketAuthorization.List.class, new ServletWebRequest(scopedRequest))).isSameAs(
            scoped
        );
        assertThat(
            source.resolve(TicketAuthorization.List.class, new ServletWebRequest(new MockHttpServletRequest()))
        ).isSameAs(global);
    }

    @Test
    @DisplayName("read and update context use the Ticket URI identity while missing URI metadata uses global")
    void shouldResolveTicketScopeWithoutExistenceLeak() {
        TicketQueryFieldContexts fields = mock(TicketQueryFieldContexts.class);
        TicketQueryFieldContextSource source = new TicketQueryFieldContextSource(fields);
        UUID ticketId = UUID.randomUUID();
        QueryFieldContext historical = new QueryFieldContext(Map.of("scope", "historical"));
        QueryFieldContext global = new QueryFieldContext(Map.of("scope", "global"));
        when(fields.forTicket(ticketId)).thenReturn(historical);
        when(fields.global()).thenReturn(global);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("ticketId", ticketId.toString()));

        assertThat(source.resolve(TicketAuthorization.Read.class, new ServletWebRequest(request))).isSameAs(historical);
        assertThat(source.resolve(TicketAuthorization.Update.class, new ServletWebRequest(request))).isSameAs(
            historical
        );
        assertThat(
            source.resolve(TicketAuthorization.Read.class, new ServletWebRequest(new MockHttpServletRequest()))
        ).isSameAs(global);
        verify(fields, Mockito.times(2)).forTicket(ticketId);
    }

    @Test
    @DisplayName("source advertises only Ticket Authorization Contracts that own runtime custom fields")
    void shouldSupportOnlyTicketAuthorizationContracts() {
        TicketQueryFieldContextSource source = new TicketQueryFieldContextSource(mock(TicketQueryFieldContexts.class));

        assertThat(source.supports(TicketAuthorization.List.class)).isTrue();
        assertThat(source.supports(TicketAuthorization.Read.class)).isTrue();
        assertThat(source.supports(TicketAuthorization.Update.class)).isTrue();
        assertThat(source.supports(String.class)).isFalse();
    }
}
