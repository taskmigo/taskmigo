package io.taskmigo.auth.authorization.object;

/// Composes typed Object Authorization predicates without exposing their representation.
public interface ObjectAuthorizationPredicates {
    /// Returns an identity predicate for the object contract.
    <Q> ObjectAuthorizationPredicate<Q> alwaysTrue();

    /// Returns a zero predicate for the object contract.
    <Q> ObjectAuthorizationPredicate<Q> alwaysFalse();

    /// Conjoins compatible Object Authorization predicates.
    <Q> ObjectAuthorizationPredicate<Q> and(
        ObjectAuthorizationPredicate<Q> left,
        ObjectAuthorizationPredicate<Q> right
    );

    /// Disjoins compatible Object Authorization predicates.
    <Q> ObjectAuthorizationPredicate<Q> or(ObjectAuthorizationPredicate<Q> left, ObjectAuthorizationPredicate<Q> right);

    /// Negates an Object Authorization predicate.
    <Q> ObjectAuthorizationPredicate<Q> not(ObjectAuthorizationPredicate<Q> predicate);

    /// Returns the standard logical composer.
    static ObjectAuthorizationPredicates standard() {
        return DefaultObjectAuthorizationPredicates.INSTANCE;
    }
}
