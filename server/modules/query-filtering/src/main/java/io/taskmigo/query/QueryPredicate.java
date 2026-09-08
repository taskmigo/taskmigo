package io.taskmigo.query;

/// An immutable opaque logical predicate for one Query Contract.
public interface QueryPredicate<Q> {
    /// Returns whether this predicate matches every value in the contract.
    boolean isAlwaysTrue();

    /// Returns whether this predicate matches no values in the contract.
    boolean isAlwaysFalse();
}
