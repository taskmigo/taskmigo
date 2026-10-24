package io.taskmigo.authorization.object.application.api;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.query.QueryFieldContext;

/// Produces typed logical Object Authorization predicates from one Request Authorization context.
public interface ObjectAuthorization {
    /// Partially evaluates Object Statements against a static authorization schema.
    default <Q> ObjectAuthorizationPredicate<Q> authorize(
        AuthorizationContext context,
        ObjectAuthorizationSchema<Q> schema
    ) {
        return this.authorize(context, schema, QueryFieldContext.empty());
    }

    /// Partially evaluates Object Statements against the exact runtime field snapshot for the contract.
    <Q> ObjectAuthorizationPredicate<Q> authorize(
        AuthorizationContext context,
        ObjectAuthorizationSchema<Q> schema,
        QueryFieldContext queryContext
    );

    /// Validates an Object Statement independently against every authorization schema governed by its target.
    void validatePolicy(String policy, String method, String path);
}
