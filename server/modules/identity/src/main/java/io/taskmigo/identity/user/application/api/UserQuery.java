package io.taskmigo.identity.user.application.api;

import io.taskmigo.query.QuerySchemaView;

/// Typed Query contracts published by the User feature.
public final class UserQuery {

    private UserQuery() {}

    public interface List extends QuerySchemaView {}
}
