package io.taskmigo.query;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// Carries transport-neutral client intent for a list query.
public record ListQueryRequest(@Nullable String filterBy, List<String> sortBy) {
    public ListQueryRequest {
        sortBy = sortBy == null ? List.of() : List.copyOf(sortBy);
    }

    /// Returns an empty list-query request.
    public static ListQueryRequest empty() {
        return new ListQueryRequest(null, List.of());
    }
}
