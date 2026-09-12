package io.taskmigo.query;

import io.taskmigo.query.persistence.QueryExpression;

/// Default Boolean-algebra implementation for opaque Query Predicates.
@SuppressWarnings("checkstyle:NeedBraces")
final class DefaultQueryPredicates implements QueryPredicates {

    static final DefaultQueryPredicates INSTANCE = new DefaultQueryPredicates();

    private DefaultQueryPredicates() {}

    @Override
    public <Q> QueryPredicate<Q> alwaysTrue() {
        return QueryPredicateFactory.wrap("", new QueryExpression.Literal(true));
    }

    @Override
    public <Q> QueryPredicate<Q> alwaysFalse() {
        return QueryPredicateFactory.wrap("", new QueryExpression.Literal(false));
    }

    @Override
    public <Q> QueryPredicate<Q> and(QueryPredicate<Q> left, QueryPredicate<Q> right) {
        requireCompatible(left, right);
        if (left.isAlwaysFalse()) return left;
        if (right.isAlwaysFalse()) return right;
        if (left.isAlwaysTrue()) return right;
        if (right.isAlwaysTrue()) return left;
        return QueryPredicateFactory.wrap(schema(left), binary(QueryExpression.BinaryOperator.AND, left, right));
    }

    @Override
    public <Q> QueryPredicate<Q> or(QueryPredicate<Q> left, QueryPredicate<Q> right) {
        requireCompatible(left, right);
        if (left.isAlwaysTrue()) return left;
        if (right.isAlwaysTrue()) return right;
        if (left.isAlwaysFalse()) return right;
        if (right.isAlwaysFalse()) return left;
        return QueryPredicateFactory.wrap(schema(left), binary(QueryExpression.BinaryOperator.OR, left, right));
    }

    @Override
    public <Q> QueryPredicate<Q> not(QueryPredicate<Q> predicate) {
        if (predicate.isAlwaysTrue()) return QueryPredicateFactory.constantLike(predicate, false);
        if (predicate.isAlwaysFalse()) return QueryPredicateFactory.constantLike(predicate, true);
        return QueryPredicateFactory.wrap(
            schema(predicate),
            new QueryExpression.Unary(
                QueryExpression.UnaryOperator.NOT,
                QueryPredicateFactory.model(predicate).expression()
            )
        );
    }

    private static <Q> QueryExpression binary(
        QueryExpression.BinaryOperator operator,
        QueryPredicate<Q> left,
        QueryPredicate<Q> right
    ) {
        return new QueryExpression.Binary(
            operator,
            QueryPredicateFactory.model(left).expression(),
            QueryPredicateFactory.model(right).expression()
        );
    }

    private static String schema(QueryPredicate<?> predicate) {
        return QueryPredicateFactory.schemaIdentity(predicate);
    }

    private static void requireCompatible(QueryPredicate<?> left, QueryPredicate<?> right) {
        if (!schema(left).equals(schema(right)) && !schema(left).isEmpty() && !schema(right).isEmpty()) {
            throw new IllegalArgumentException("Query Predicates belong to incompatible schemas");
        }
    }
}
