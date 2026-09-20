package io.taskmigo.identity.group.application.port.out;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.identity.group.GroupInfo;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.UUID;

/// Reads Group projections and existence state without hydrating mutation aggregates.
public interface GroupQueryRepository {
    boolean exists(UUID id);

    boolean containsAll(Collection<UUID> ids);

    OffsetPage<GroupInfo> list(
        int page,
        int perPage,
        QueryPredicate<GroupInfo> filter,
        ObjectAuthorizationPredicate<GroupInfo> authorization
    );
}
