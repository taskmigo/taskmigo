package io.taskmigo.query;

import java.util.Objects;

/// Carries one request-scoped logical predicate for a typed Query Contract.
public record AuthorizedQuery<Q>(QueryPredicate<Q> predicate) {
    public AuthorizedQuery {
        Objects.requireNonNull(predicate);
    }
}
