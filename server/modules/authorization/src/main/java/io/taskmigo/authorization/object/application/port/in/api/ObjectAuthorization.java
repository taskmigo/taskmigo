package io.taskmigo.authorization.object.application.port.in.api;

import io.taskmigo.authorization.object.ObjectAuthorizationBinding;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.request.AuthorizationContext;

/// Produces typed logical Object Authorization predicates from one Request Authorization context.
public interface ObjectAuthorization {
    /// Produces a predicate from the shared semantic resource binding.
    default <Q> ObjectAuthorizationPredicate<Q> authorize(
        AuthorizationContext context,
        ObjectAuthorizationBinding<Q> binding
    ) {
        throw new UnsupportedOperationException("Object Authorization binding is not implemented");
    }

    /// Validates an Object Statement independently against every route schema governed by its target.
    void validatePolicy(String policy, String method, String path);
}
