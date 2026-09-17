package io.taskmigo.identity.persistence.query;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import org.springframework.data.jpa.domain.Specification;

/// Binds one resource-owned Object Authorization predicate to its persistence model.
public interface ObjectAuthorizationPredicateBinder<Q, E> {
    /// Returns the logical object contract accepted by this binder.
    Class<Q> objectType();

    /// Returns the persistence entity queried by this binder.
    Class<E> domainType();

    /// Converts a trusted logical predicate into a database-side specification.
    Specification<E> bind(ObjectAuthorizationPredicate<Q> predicate);
}
