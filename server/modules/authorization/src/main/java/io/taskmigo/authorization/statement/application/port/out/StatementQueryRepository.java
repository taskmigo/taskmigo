package io.taskmigo.authorization.statement.application.port.out;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.UUID;

/// Reads Statement projections and existence state without hydrating mutation aggregates.
public interface StatementQueryRepository {
    boolean containsAll(Collection<UUID> ids);

    OffsetPage<StatementInfo> list(int page, int perPage);

    OffsetPage<StatementInfo> list(
        int page,
        int perPage,
        QueryPredicate<StatementInfo> filter,
        ObjectAuthorizationPredicate<StatementInfo> authorization
    );
}
