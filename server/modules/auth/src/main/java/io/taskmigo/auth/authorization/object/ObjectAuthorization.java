package io.taskmigo.auth.authorization.object;

import io.taskmigo.auth.authorization.request.AuthorizationContext;
import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.QuerySchema;

/// Produces a typed logical object predicate from one Request Authorization context.
public interface ObjectAuthorization {
    /// Partially evaluates object Statements against the selected logical Query Schema.
    <Q> QueryPredicate<Q> authorize(AuthorizationContext context, QuerySchema<Q> schema);
}
