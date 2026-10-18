package io.taskmigo.web.api.support.query;

import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QuerySchemaView;
import java.util.Objects;

/// Holds the Query Schema and immutable runtime field snapshot selected for the current handler.
public record ResolvedQueryRequestContext(QuerySchemaView schema, QueryFieldContext queryContext) {
    public ResolvedQueryRequestContext {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(queryContext);
    }
}
