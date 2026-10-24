package io.taskmigo.authorization.object.application.api;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.request.AuthorizationPrincipal;
import io.taskmigo.authorization.request.AuthorizationRequest;
import io.taskmigo.query.QueryFieldContext;

/// Authorizes an explicit principal/request pair and produces its Object Authorization predicate.
///
/// This is the framework-neutral entry point for workers and other non-HTTP callers acting as a user.
public interface AuthorizationGateway {
    default <Q> ObjectAuthorizationPredicate<Q> authorize(
        AuthorizationPrincipal principal,
        AuthorizationRequest request,
        ObjectAuthorizationSchema<Q> schema
    ) {
        return this.authorize(principal, request, schema, QueryFieldContext.empty());
    }

    <Q> ObjectAuthorizationPredicate<Q> authorize(
        AuthorizationPrincipal principal,
        AuthorizationRequest request,
        ObjectAuthorizationSchema<Q> schema,
        QueryFieldContext context
    );
}
