package io.taskmigo.query;

import io.taskmigo.query.model.QueryExpression;
import io.taskmigo.query.model.QueryPredicateModel;
import java.util.Objects;

/// Creates and composes opaque predicates at the logical query boundary.
final class QueryPredicateFactory {

    private QueryPredicateFactory() {}

    static <Q> QueryPredicate<Q> from(QueryBinding<Q> binding, QueryExpression expression) {
        Objects.requireNonNull(binding);
        return new LogicalQueryPredicate<>(binding.identity(), Objects.requireNonNull(expression));
    }

    static <Q> QueryPredicate<Q> alwaysTrue(QueryBinding<Q> binding) {
        return from(binding, new QueryExpression.Literal(true));
    }

    static <Q> QueryPredicate<Q> alwaysFalse(QueryBinding<Q> binding) {
        return from(binding, new QueryExpression.Literal(false));
    }

    static QueryPredicateModel model(QueryPredicate<?> predicate) {
        if (!(predicate instanceof QueryPredicateModel model)) {
            throw new IllegalArgumentException("unsupported Query Predicate implementation");
        }
        return model;
    }

    static String bindingIdentity(QueryPredicate<?> predicate) {
        return model(predicate).bindingIdentity();
    }

    static <Q> QueryPredicate<Q> wrap(String bindingIdentity, QueryExpression expression) {
        return new LogicalQueryPredicate<>(bindingIdentity, expression);
    }

    static <Q> QueryPredicate<Q> constantLike(QueryPredicate<?> predicate, boolean value) {
        return wrap(bindingIdentity(predicate), new QueryExpression.Literal(value));
    }

    private record LogicalQueryPredicate<Q>(
        String bindingIdentity,
        QueryExpression expression
    ) implements QueryPredicate<Q>, QueryPredicateModel {
        private LogicalQueryPredicate {
            Objects.requireNonNull(bindingIdentity);
            Objects.requireNonNull(expression);
        }

        @Override
        public boolean isAlwaysTrue() {
            return this.expression instanceof QueryExpression.Literal literal && Boolean.TRUE.equals(literal.value());
        }

        @Override
        public boolean isAlwaysFalse() {
            return this.expression instanceof QueryExpression.Literal literal && Boolean.FALSE.equals(literal.value());
        }
    }
}
