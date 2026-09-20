package io.taskmigo.authorization.object.domain;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationPredicates;
import io.taskmigo.authorization.statement.Effect;
import java.util.List;
import java.util.Objects;

/// Applies pure allow/deny composition semantics to already-derived Object Authorization predicates.
public final class ObjectAuthorizationPredicateComposer {

    private final ObjectAuthorizationPredicates predicates;

    /// Creates a composer over the persistence-neutral logical predicate operations owned by Authorization.
    ///
    /// @param predicates logical Boolean operations for opaque Object Authorization predicates
    public ObjectAuthorizationPredicateComposer(ObjectAuthorizationPredicates predicates) {
        this.predicates = Objects.requireNonNull(predicates);
    }

    /// Composes one operation as any allow predicate and not any deny predicate.
    ///
    /// @param zero schema-compatible constant-false predicate used as the identity for both predicate sets
    /// @param rules already-derived target-matching predicates for one immutable authorization operation
    /// @return the final simplified Object Authorization predicate
    public <Q> ObjectAuthorizationPredicate<Q> compose(ObjectAuthorizationPredicate<Q> zero, List<Rule<Q>> rules) {
        Objects.requireNonNull(zero);
        List<Rule<Q>> operationRules = List.copyOf(rules);
        ObjectAuthorizationPredicate<Q> allows = zero;
        ObjectAuthorizationPredicate<Q> denies = zero;
        for (Rule<Q> rule : operationRules) {
            if (rule.effect() == Effect.ALLOW) {
                allows = this.predicates.or(allows, rule.predicate());
            } else {
                denies = this.predicates.or(denies, rule.predicate());
            }
        }
        return this.predicates.and(allows, this.predicates.not(denies));
    }

    /// Couples a Statement effect with the already-derived predicate for that Statement.
    public record Rule<Q>(Effect effect, ObjectAuthorizationPredicate<Q> predicate) {
        public Rule {
            Objects.requireNonNull(effect);
            Objects.requireNonNull(predicate);
        }
    }
}
