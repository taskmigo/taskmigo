package io.taskmigo.authorization.object.model;

/// Trusted logical view implemented by Object Authorization-owned opaque predicates for resource-owned binders.
public interface ObjectAuthorizationPredicateModel {
    ObjectAuthorizationExpression expression();

    String schemaIdentity();
}
