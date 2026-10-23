package io.taskmigo.ticket.application.api;

import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.ListQueryRequest;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines Ticket creation, query, and historical-schema-preserving mutation use cases.
public interface TicketService {
    UUID create(UUID ticketTypeId, String title, String status, Map<String, ?> customFields, UUID actorId);

    OffsetPage<TicketInfo> list(
        int page,
        int perPage,
        @Nullable UUID ticketTypeId,
        ListQueryRequest query,
        AuthorizationContext authorization
    );

    TicketInfo read(UUID ticketId, AuthorizationContext authorization);

    TicketInfo update(UUID ticketId, TicketPatch patch, AuthorizationContext authorization);
}
