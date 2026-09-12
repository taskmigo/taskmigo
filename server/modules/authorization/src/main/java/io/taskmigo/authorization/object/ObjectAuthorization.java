package io.taskmigo.authorization.object;

import io.taskmigo.authorization.request.AuthorizationContext;

/// Produces a typed logical Object Authorization predicate from one Request Authorization context.
public interface ObjectAuthorization {
    /// Partially evaluates object Statements against the selected Object Authorization Schema.
    <Q> ObjectAuthorizationPredicate<Q> authorize(AuthorizationContext context, ObjectAuthorizationSchema<Q> schema);

    /// Validates an Object Statement independently against every route schema governed by its target.
    void validatePolicy(String policy, String method, String path);
}
