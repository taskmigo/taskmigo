package io.taskmigo.authorization.object.application.port.in.api;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.request.AuthorizationContext;

/// Produces typed logical Object Authorization predicates from one Request Authorization context.
public interface ObjectAuthorization {
    /// Partially evaluates Object Statements against the selected Object Authorization schema.
    <Q> ObjectAuthorizationPredicate<Q> authorize(AuthorizationContext context, ObjectAuthorizationSchema<Q> schema);

    /// Validates an Object Statement independently against every route schema governed by its target.
    void validatePolicy(String policy, String method, String path);
}
