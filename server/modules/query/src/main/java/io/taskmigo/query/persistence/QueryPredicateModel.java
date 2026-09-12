package io.taskmigo.query.persistence;

/// Trusted persistence view implemented by Query-owned opaque predicates.
public interface QueryPredicateModel {
    QueryExpression expression();

    String schemaIdentity();
}
