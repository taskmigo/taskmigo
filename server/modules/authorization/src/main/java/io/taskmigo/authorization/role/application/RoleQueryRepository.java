package io.taskmigo.authorization.role.application;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.role.RoleInfo;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/// Reads Role projections and existence state without hydrating command aggregates.
public interface RoleQueryRepository {
    boolean containsAll(Collection<UUID> ids);

    OffsetPage<RoleInfo> list(
        int page,
        int perPage,
        QueryPredicate<RoleInfo> filter,
        ObjectAuthorizationPredicate<RoleInfo> authorization
    );

    List<RoleInfo> findByIds(Collection<UUID> ids);
}
