package io.taskmigo.authorization;

/// Validates whether a record-visibility authorization predicate can be compiled by a database query backend.
public interface AuthorizationObjectQueryDialect {
    /// Returns the HTTP method of the database-backed operation supported by this dialect.
    String method();

    /// Returns a concrete normalized request path handled by this dialect.
    String path();

    /// Validates a parsed object predicate before the Statement becomes active.
    ///
    /// @param expression the Taskmigo authorization AST to validate
    /// @throws IllegalArgumentException if the predicate cannot be compiled by this query backend
    void validate(AuthorizationExpression expression);
}
