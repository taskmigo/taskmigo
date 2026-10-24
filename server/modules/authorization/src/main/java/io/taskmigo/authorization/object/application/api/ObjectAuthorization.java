package io.taskmigo.authorization.object.application.api;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QuerySchemaView;

/// Produces typed logical Object Authorization predicates from one Request Authorization context.
public interface ObjectAuthorization {
    /// Partially evaluates Object Statements against a static operation query schema.
    default <Q> ObjectAuthorizationPredicate<Q> authorize(AuthorizationContext context, QuerySchemaView schema) {
        return this.authorize(context, schema, QueryFieldContext.empty());
    }

    /// Partially evaluates Object Statements against the exact runtime query-field snapshot for the operation.
    <Q> ObjectAuthorizationPredicate<Q> authorize(
        AuthorizationContext context,
        QuerySchemaView schema,
        QueryFieldContext queryContext
    );

    /// Validates an Object Statement independently against every route schema governed by its target.
    void validatePolicy(String policy, String method, String path);
}
