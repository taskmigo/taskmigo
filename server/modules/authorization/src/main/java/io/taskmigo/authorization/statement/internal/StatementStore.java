package io.taskmigo.authorization.statement.internal;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.statement.StatementDefinition;
import io.taskmigo.authorization.statement.StatementInfo;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/// Defines persistence operations needed by Statement application use cases.
public interface StatementStore {
    boolean existsByName(String name);

    UUID create(StatementDefinition definition);

    Optional<UUID> findIdByName(String name);

    void update(UUID id, StatementDefinition definition);

    OffsetPage<StatementInfo> list(int page, int perPage);

    OffsetPage<StatementInfo> list(
        int page,
        int perPage,
        QueryPredicate<StatementInfo> filter,
        ObjectAuthorizationPredicate<StatementInfo> authorization
    );

    boolean containsAll(Collection<UUID> ids);
}
