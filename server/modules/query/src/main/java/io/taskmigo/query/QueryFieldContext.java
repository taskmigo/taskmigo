package io.taskmigo.query;

import java.util.Map;
import java.util.Objects;

/// Carries operation inputs used to resolve runtime query fields without coupling schema contracts to persistence.
public record QueryFieldContext(Map<String, Object> values) {
    private static final QueryFieldContext EMPTY = new QueryFieldContext(Map.of());

    public QueryFieldContext {
        Objects.requireNonNull(values);
        values = Map.copyOf(values);
    }

    /// Returns the context used by schemas that expose only static fields.
    public static QueryFieldContext empty() {
        return EMPTY;
    }
}
