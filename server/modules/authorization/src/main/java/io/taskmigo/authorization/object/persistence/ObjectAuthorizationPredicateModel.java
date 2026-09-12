package io.taskmigo.authorization.object.persistence;

/// Trusted persistence view implemented by Object Authorization-owned opaque predicates.
public interface ObjectAuthorizationPredicateModel {
    ObjectAuthorizationExpression expression();

    String schemaIdentity();
}
