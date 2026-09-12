package io.taskmigo.authorization.object;

/// Represents an opaque logical predicate produced by Object Authorization.
public interface ObjectAuthorizationPredicate<Q> {
    /// Returns whether this predicate grants visibility to every object.
    boolean isAlwaysTrue();

    /// Returns whether this predicate grants visibility to no objects.
    boolean isAlwaysFalse();
}
