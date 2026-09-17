package io.taskmigo.authorization.persistence.query;

import io.taskmigo.query.QueryPredicate;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.domain.Specification;

/// Binds one Access Control-owned Query Predicate to its persistence model.
public interface QueryPredicateBinder<Q, E> {
    /// Returns the logical query contract accepted by this binder.
    Class<Q> queryType();

    /// Returns the persistence entity queried by this binder.
    Class<E> domainType();

    /// Converts a trusted logical predicate into a database-side specification.
    Specification<E> bind(@NonNull QueryPredicate<Q> predicate);
}
