package io.taskmigo.auth;

import java.util.Map;

/// Provides the resource-specific database-query boundary for object authorization.
@SuppressWarnings("NullableProblems")
public interface AuthorizationObjectQueryDialect {
    /// Returns the HTTP method handled by this query dialect.
    String method();

    /// Returns the concrete request path handled by this query dialect.
    String path();

    /// Returns the queryable object fields and their persisted Java types.
    Map<String, Class<?>> fields();

    /// Validates that an object predicate can be translated to this resource's database query.
    ///
    /// @param predicate the specialized allow or deny predicate
    /// @throws AuthorizationException when the resource cannot translate the predicate safely
    void validate(AuthorizationCompiler.Expression predicate);
}
