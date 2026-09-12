package io.taskmigo.authorization.object.persistence;

import io.taskmigo.authorization.object.ObjectAuthorizationPredicate;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import java.util.Objects;

/// Creates and exposes the trusted persistence model behind opaque Object Authorization predicates.
public final class ObjectAuthorizationPredicateModels {

    private ObjectAuthorizationPredicateModels() {}

    public static <Q> ObjectAuthorizationPredicate<Q> from(
        ObjectAuthorizationSchema<Q> schema,
        ObjectAuthorizationExpression expression
    ) {
        Objects.requireNonNull(schema);
        return wrap(schema.identity(), expression);
    }

    public static <Q> ObjectAuthorizationPredicate<Q> constant(ObjectAuthorizationSchema<Q> schema, boolean value) {
        return from(schema, new ObjectAuthorizationExpression.Literal(value));
    }

    public static <Q> ObjectAuthorizationPredicate<Q> constantLike(
        ObjectAuthorizationPredicate<?> predicate,
        boolean value
    ) {
        return wrap(schemaIdentity(predicate), new ObjectAuthorizationExpression.Literal(value));
    }

    public static <Q> ObjectAuthorizationPredicate<Q> wrap(
        String schemaIdentity,
        ObjectAuthorizationExpression expression
    ) {
        return new LogicalObjectAuthorizationPredicate<>(
            Objects.requireNonNull(schemaIdentity),
            Objects.requireNonNull(expression)
        );
    }

    public static ObjectAuthorizationPredicateModel model(ObjectAuthorizationPredicate<?> predicate) {
        if (!(predicate instanceof ObjectAuthorizationPredicateModel model)) {
            throw new IllegalArgumentException("unsupported Object Authorization Predicate implementation");
        }
        return model;
    }

    public static String schemaIdentity(ObjectAuthorizationPredicate<?> predicate) {
        return model(predicate).schemaIdentity();
    }

    private record LogicalObjectAuthorizationPredicate<Q>(
        String schemaIdentity,
        ObjectAuthorizationExpression expression
    ) implements ObjectAuthorizationPredicate<Q>, ObjectAuthorizationPredicateModel {
        @Override
        public boolean isAlwaysTrue() {
            return this.expression instanceof ObjectAuthorizationExpression.Literal literal &&
                Boolean.TRUE.equals(literal.value());
        }

        @Override
        public boolean isAlwaysFalse() {
            return this.expression instanceof ObjectAuthorizationExpression.Literal literal &&
                Boolean.FALSE.equals(literal.value());
        }
    }
}
