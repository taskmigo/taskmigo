package io.taskmigo.identity.adapter.out.persistence.query;

import io.taskmigo.jpaquery.JpaQueryExpressionBinder;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.QueryPredicate;
import io.taskmigo.query.model.QueryPredicateModel;
import org.springframework.data.jpa.domain.Specification;

/// Binds Query predicates through the operation-scoped JPA query schema owned by the query implementation.
public final class JpaQueryPredicateBinder<Q, E> implements QueryPredicateBinder<Q, E> {

    private final Class<Q> queryType;
    private final QuerySchema<E> schema;

    /// Creates a binder backed by one concrete operation schema.
    public JpaQueryPredicateBinder(Class<Q> queryType, QuerySchema<E> schema) {
        this.queryType = queryType;
        this.schema = schema;
    }

    @Override
    public Class<Q> queryType() {
        return this.queryType;
    }

    @Override
    public Class<E> domainType() {
        return this.schema.rootType();
    }

    @Override
    public Specification<E> bind(QueryPredicate<Q> predicate) {
        if (!(predicate instanceof QueryPredicateModel model)) {
            throw new IllegalArgumentException("unsupported Query Predicate implementation");
        }
        if (!model.schemaIdentity().equals(this.schema.identity())) {
            throw new IllegalArgumentException("Query Predicate schema identity does not match the JPA operation schema");
        }
        return JpaQueryExpressionBinder.bind(model.expression(), this.schema);
    }
}
