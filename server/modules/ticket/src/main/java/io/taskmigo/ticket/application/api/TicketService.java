package io.taskmigo.ticket.application.api;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.FilteredQuery;
import io.taskmigo.query.QueryFieldContext;
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
        FilteredQuery<TicketInfo> query,
        ObjectAuthorizationPredicate<TicketInfo> authorization
    );

    TicketInfo read(UUID ticketId, ObjectAuthorizationPredicate<TicketInfo> authorization, QueryFieldContext context);

    TicketInfo update(
        UUID ticketId,
        TicketPatch patch,
        ObjectAuthorizationPredicate<TicketInfo> authorization,
        QueryFieldContext context
    );
}
