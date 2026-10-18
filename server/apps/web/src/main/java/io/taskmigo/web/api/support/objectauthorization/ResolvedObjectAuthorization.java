package io.taskmigo.web.api.support.objectauthorization;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.query.QueryFieldContext;
import java.util.Objects;

/// Carries server-derived Object Authorization with the exact Query field snapshot used to derive it.
public record ResolvedObjectAuthorization<Q>(
    ObjectAuthorizationPredicate<Q> predicate,
    QueryFieldContext queryContext
) {
    public ResolvedObjectAuthorization {
        Objects.requireNonNull(predicate);
        Objects.requireNonNull(queryContext);
    }
}
