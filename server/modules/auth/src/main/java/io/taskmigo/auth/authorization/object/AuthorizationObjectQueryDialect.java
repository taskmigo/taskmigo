package io.taskmigo.auth.authorization.object;


/// Provides the resource-specific database-query boundary for object authorization.
public interface AuthorizationObjectQueryDialect extends FilterSchema {
    /// Returns the HTTP method handled by this query dialect.
    String method();

    /// Returns the concrete request path handled by this query dialect.
    String path();

}
