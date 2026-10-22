package io.taskmigo.query;

import java.util.List;
import java.util.Objects;

/// Carries the client predicate, immutable runtime field context, and requested sorting for one list query specification.
public record ListQuerySpec<Q>(QueryPredicate<Q> predicate, QueryFieldContext context, List<QuerySort> sorting) {
    public ListQuerySpec {
        Objects.requireNonNull(predicate);
        Objects.requireNonNull(context);
        sorting = List.copyOf(Objects.requireNonNull(sorting));
    }

    /// Creates a static-context query with no requested sorting.
    public ListQuerySpec(QueryPredicate<Q> predicate) {
        this(predicate, QueryFieldContext.empty(), List.of());
    }
}
