package io.taskmigo.web.api.v0.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.api.TicketQueryFieldContexts;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.servlet.HandlerMapping;

class TicketQueryFieldContextSourceTest {

    @Test
    @DisplayName("all Ticket operations use the same registry namespace independent of URI and type")
    void shouldResolveSharedSnapshot() {
        TicketQueryFieldContexts fields = mock(TicketQueryFieldContexts.class);
        TicketQueryFieldContextSource source = new TicketQueryFieldContextSource(fields);
        QueryFieldContext snapshot = new QueryFieldContext(Map.of("scope", "registry"));
        when(fields.snapshot()).thenReturn(snapshot);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("ticketTypeId", UUID.randomUUID().toString());
        request.setAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            Map.of("ticketId", UUID.randomUUID().toString())
        );

        for (String operation : List.of("tickets.list", "tickets.read", "tickets.update")) {
            assertThat(source.supports(operation)).isTrue();
            assertThat(source.resolve(operation, new ServletWebRequest(request))).isSameAs(snapshot);
            assertThat(source.resolve(operation, new ServletWebRequest(new MockHttpServletRequest()))).isSameAs(
                snapshot
            );
        }
        assertThat(source.supports("users.list")).isFalse();
    }
}
