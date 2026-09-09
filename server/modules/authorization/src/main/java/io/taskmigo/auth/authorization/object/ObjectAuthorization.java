package io.taskmigo.auth.authorization.object;

import io.taskmigo.auth.authorization.request.AuthorizationContext;

/// Produces a typed logical Object Authorization predicate from one Request Authorization context.
public interface ObjectAuthorization {
    /// Partially evaluates object Statements against the selected Object Authorization Schema.
    <Q> ObjectAuthorizationPredicate<Q> authorize(AuthorizationContext context, ObjectAuthorizationSchema<Q> schema);
}
