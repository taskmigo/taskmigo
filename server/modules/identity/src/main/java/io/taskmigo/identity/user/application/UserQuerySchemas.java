package io.taskmigo.identity.user.application;

import io.taskmigo.query.QuerySchemaView;

/// Provides the operation-scoped User query schemas required by User use cases.
public interface UserQuerySchemas {
    QuerySchemaView list();
    QuerySchemaView delete();
    QuerySchemaView updateStatements();
}
