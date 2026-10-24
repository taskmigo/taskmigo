package io.taskmigo.identity.user.application;

import io.taskmigo.query.QuerySchemaView;

/// Provides the User list query schema required by query compilation.
public interface UserQuerySchemas {
    QuerySchemaView list();
}
