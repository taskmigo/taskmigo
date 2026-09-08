package io.taskmigo.auth.resourcequery;

import io.taskmigo.query.QueryPredicate;
import org.springframework.data.jpa.domain.Specification;

/// Binds one resource-owned logical Query Predicate to its persistence model.
public interface QueryPredicateBinder<Q, E> {
    /// Returns the logical query contract accepted by this binder.
    Class<Q> queryType();

    /// Returns the persistence entity queried by this binder.
    Class<E> domainType();

    /// Converts a trusted logical predicate into a database-side specification.
    Specification<E> bind(QueryPredicate<Q> predicate);
}
