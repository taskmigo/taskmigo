package io.taskmigo.auth.authorization.object;

import java.util.Map;

/// Provides the resource-specific database-query boundary for object authorization.
public interface AuthorizationObjectQueryDialect {
    /// Returns the HTTP method handled by this query dialect.
    String method();

    /// Returns the concrete request path handled by this query dialect.
    String path();

    /// Returns the queryable object fields and their persisted Java types.
    Map<String, Class<?>> fields();
}
