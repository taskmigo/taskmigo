package io.taskmigo.authorization.statement;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.foundation.OffsetPage;
import io.taskmigo.query.QueryPredicate;
import java.util.Collection;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Defines the application contract for Statement use cases.
public interface StatementService {
    UUID create(
        @Nullable String code,
        @Nullable String description,
        @Nullable Effect effect,
        @Nullable Scope scope,
        @Nullable String method,
        @Nullable String path,
        @Nullable String policy
    );
    OffsetPage<StatementInfo> list(int page, int perPage);
    OffsetPage<StatementInfo> list(
        int page,
        int perPage,
        QueryPredicate<StatementInfo> filter,
        ObjectAuthorizationPredicate<StatementInfo> authorization
    );
    void requireStatements(Collection<UUID> ids);
}
