package io.taskmigo.query;

import io.taskmigo.query.model.QueryExpression;
import io.taskmigo.query.model.QueryPredicateModel;
import java.util.Objects;

/// Creates and composes opaque predicates at the logical query boundary.
final class QueryPredicateFactory {

    private QueryPredicateFactory() {}

    static QueryPredicateModel model(QueryPredicate<?> predicate) {
        if (!(predicate instanceof QueryPredicateModel model)) {
            throw new IllegalArgumentException("unsupported Query Predicate implementation");
        }
        return model;
    }

    static String schemaIdentity(QueryPredicate<?> predicate) {
        return model(predicate).schemaIdentity();
    }

    static <Q> QueryPredicate<Q> wrap(String schemaIdentity, QueryExpression expression) {
        return new LogicalQueryPredicate<>(schemaIdentity, expression);
    }

    static <Q> QueryPredicate<Q> constantLike(QueryPredicate<?> predicate, boolean value) {
        return wrap(schemaIdentity(predicate), new QueryExpression.Literal(value));
    }

    private record LogicalQueryPredicate<Q>(
        String schemaIdentity,
        QueryExpression expression
    ) implements QueryPredicate<Q>, QueryPredicateModel {
        private LogicalQueryPredicate {
            Objects.requireNonNull(schemaIdentity);
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
