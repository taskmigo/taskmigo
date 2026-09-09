package io.taskmigo.query;

import java.util.Objects;

/// Carries the request-scoped client filter for one typed Query Contract.
public record FilteredQuery<Q>(QueryPredicate<Q> predicate) {
    public FilteredQuery {
        Objects.requireNonNull(predicate);
    }
}
