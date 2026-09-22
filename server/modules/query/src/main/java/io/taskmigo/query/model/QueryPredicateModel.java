package io.taskmigo.query.model;

/// Trusted logical view implemented by Query-owned opaque predicates.
public interface QueryPredicateModel {
    QueryExpression expression();

    String schemaIdentity();
}
