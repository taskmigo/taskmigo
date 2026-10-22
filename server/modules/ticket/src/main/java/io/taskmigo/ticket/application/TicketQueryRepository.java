package io.taskmigo.ticket.application;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.ListQuerySpec;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.ticket.application.api.TicketInfo;
import io.taskmigo.ticket.domain.Ticket;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Resolves Ticket query targets with filtering and Object Authorization applied in persistence.
public interface TicketQueryRepository {
    @SuppressWarnings("NullableProblems")
    Optional<Ticket> findAuthorized(
        UUID id,
        ObjectAuthorizationPredicate<TicketInfo> authorization,
        QueryFieldContext context
    );

    @SuppressWarnings("NullableProblems")
    OffsetPage<Ticket> list(
        int page,
        int perPage,
        @Nullable UUID ticketTypeId,
        ListQuerySpec<TicketInfo> query,
        ObjectAuthorizationPredicate<TicketInfo> authorization
    );
}
