package io.taskmigo.authorization.statement.application.api;

import io.taskmigo.query.QuerySchemaView;

/// Typed Query contracts published by the Statement feature.
public final class StatementQuery {

    private StatementQuery() {}

    public interface List extends QuerySchemaView {}
}
