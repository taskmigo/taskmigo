package io.taskmigo.authorization.object;

import io.taskmigo.authorization.object.persistence.ObjectAuthorizationExpression;
import io.taskmigo.authorization.object.persistence.ObjectAuthorizationPredicateModels;

/// Applies Boolean identities while composing Object Authorization predicates.
final class DefaultObjectAuthorizationPredicates implements ObjectAuthorizationPredicates {

    static final DefaultObjectAuthorizationPredicates INSTANCE = new DefaultObjectAuthorizationPredicates();

    private DefaultObjectAuthorizationPredicates() {}

    @Override
    public <Q> ObjectAuthorizationPredicate<Q> alwaysTrue() {
        return ObjectAuthorizationPredicateModels.wrap("", new ObjectAuthorizationExpression.Literal(true));
    }

    @Override
    public <Q> ObjectAuthorizationPredicate<Q> alwaysFalse() {
        return ObjectAuthorizationPredicateModels.wrap("", new ObjectAuthorizationExpression.Literal(false));
    }

    @Override
    public <Q> ObjectAuthorizationPredicate<Q> and(
        ObjectAuthorizationPredicate<Q> left,
        ObjectAuthorizationPredicate<Q> right
    ) {
        requireCompatible(left, right);
        if (left.isAlwaysFalse()) {
            return left;
        }
        if (right.isAlwaysFalse()) {
            return right;
        }
        if (left.isAlwaysTrue()) {
            return right;
        }
        if (right.isAlwaysTrue()) {
            return left;
        }
        return wrap(left, ObjectAuthorizationExpression.BinaryOperator.AND, right);
    }

    @Override
    public <Q> ObjectAuthorizationPredicate<Q> or(
        ObjectAuthorizationPredicate<Q> left,
        ObjectAuthorizationPredicate<Q> right
    ) {
        requireCompatible(left, right);
        if (left.isAlwaysTrue()) {
            return left;
        }
        if (right.isAlwaysTrue()) {
            return right;
        }
        if (left.isAlwaysFalse()) {
            return right;
        }
        if (right.isAlwaysFalse()) {
            return left;
        }
        return wrap(left, ObjectAuthorizationExpression.BinaryOperator.OR, right);
    }

    @Override
    public <Q> ObjectAuthorizationPredicate<Q> not(ObjectAuthorizationPredicate<Q> predicate) {
        if (predicate.isAlwaysTrue()) {
            return ObjectAuthorizationPredicateModels.constantLike(predicate, false);
        }
        if (predicate.isAlwaysFalse()) {
            return ObjectAuthorizationPredicateModels.constantLike(predicate, true);
        }
        return ObjectAuthorizationPredicateModels.wrap(
            ObjectAuthorizationPredicateModels.schemaIdentity(predicate),
            new ObjectAuthorizationExpression.Unary(
                ObjectAuthorizationExpression.UnaryOperator.NOT,
                ObjectAuthorizationPredicateModels.model(predicate).expression()
            )
        );
    }

    private static <Q> ObjectAuthorizationPredicate<Q> wrap(
        ObjectAuthorizationPredicate<Q> left,
        ObjectAuthorizationExpression.BinaryOperator operator,
        ObjectAuthorizationPredicate<Q> right
    ) {
        return ObjectAuthorizationPredicateModels.wrap(
            ObjectAuthorizationPredicateModels.schemaIdentity(left),
            new ObjectAuthorizationExpression.Binary(
                operator,
                ObjectAuthorizationPredicateModels.model(left).expression(),
                ObjectAuthorizationPredicateModels.model(right).expression()
            )
        );
    }

    private static void requireCompatible(ObjectAuthorizationPredicate<?> left, ObjectAuthorizationPredicate<?> right) {
        String leftIdentity = ObjectAuthorizationPredicateModels.schemaIdentity(left);
        String rightIdentity = ObjectAuthorizationPredicateModels.schemaIdentity(right);
        if (!leftIdentity.isEmpty() && !rightIdentity.isEmpty() && !leftIdentity.equals(rightIdentity)) {
            throw new IllegalArgumentException("Object Authorization Predicates belong to incompatible schemas");
        }
    }
}
