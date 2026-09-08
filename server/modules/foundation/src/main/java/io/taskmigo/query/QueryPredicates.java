package io.taskmigo.query;

/// Composes typed logical Query Predicates without exposing their internal representation.
public interface QueryPredicates {
    /// Returns an identity predicate for the contract.
    <Q> QueryPredicate<Q> alwaysTrue();

    /// Returns a zero predicate for the contract.
    <Q> QueryPredicate<Q> alwaysFalse();

    /// Conjoins predicates for the same contract.
    <Q> QueryPredicate<Q> and(QueryPredicate<Q> left, QueryPredicate<Q> right);

    /// Disjoins predicates for the same contract.
    <Q> QueryPredicate<Q> or(QueryPredicate<Q> left, QueryPredicate<Q> right);

    /// Negates a predicate for the same contract.
    <Q> QueryPredicate<Q> not(QueryPredicate<Q> predicate);

    /// Composes predicates whose contract is selected reflectively by a web adapter.
    @SuppressWarnings("unchecked")
    default QueryPredicate<?> andUntyped(QueryPredicate<?> left, QueryPredicate<?> right) {
        return this.and((QueryPredicate<Object>) left, (QueryPredicate<Object>) right);
    }

    /// Returns the standard logical composer.
    static QueryPredicates standard() {
        return DefaultQueryPredicates.INSTANCE;
    }
}
