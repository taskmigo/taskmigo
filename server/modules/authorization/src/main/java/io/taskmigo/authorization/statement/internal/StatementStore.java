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
    boolean existsByCode(String code);

    UUID create(StatementDefinition definition);

    Optional<StatementInfo> findByCode(String code);

    Optional<UUID> findIdByCode(String code);

    void update(UUID id, StatementDefinition definition);

    void delete(UUID id);

    OffsetPage<StatementInfo> list(int page, int perPage);

    OffsetPage<StatementInfo> list(
        int page,
        int perPage,
        QueryPredicate<StatementInfo> filter,
        ObjectAuthorizationPredicate<StatementInfo> authorization
    );

    boolean containsAll(Collection<UUID> ids);
}
