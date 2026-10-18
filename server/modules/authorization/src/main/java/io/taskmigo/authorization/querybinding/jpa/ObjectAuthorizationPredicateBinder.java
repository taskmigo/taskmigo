package io.taskmigo.authorization.querybinding.jpa;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.query.QueryFieldContext;
import org.springframework.data.jpa.domain.Specification;

/// Binds one Access Control-owned Object Authorization predicate to its persistence model.
public interface ObjectAuthorizationPredicateBinder<Q, E> {
    /// Returns the logical object contract accepted by this binder.
    Class<Q> objectType();

    /// Returns the persistence entity queried by this binder.
    Class<E> domainType();

    /// Converts a trusted logical predicate using the static query-field context.
    default Specification<E> bind(ObjectAuthorizationPredicate<Q> predicate) {
        return this.bind(predicate, QueryFieldContext.empty());
    }

    /// Converts a trusted logical predicate using the exact runtime query-field snapshot that produced it.
    Specification<E> bind(ObjectAuthorizationPredicate<Q> predicate, QueryFieldContext context);
}
