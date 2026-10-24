package io.taskmigo.authorization.role.application.api;

import io.taskmigo.query.QuerySchemaView;

/// Typed Query contracts published by the Role feature.
public final class RoleQuery {
    private RoleQuery() {}

    public interface List extends QuerySchemaView {}
}
