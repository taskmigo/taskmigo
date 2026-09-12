package io.taskmigo.query;

import io.taskmigo.query.persistence.QueryExpression;
import io.taskmigo.query.persistence.QueryPredicateModel;
import java.util.Objects;

/// Creates and composes opaque predicates at the logical query boundary.
final class QueryPredicateFactory {

    private QueryPredicateFactory() {}

    static <Q> QueryPredicate<Q> from(QuerySchema<Q> schema, QueryExpression expression) {
        Objects.requireNonNull(schema);
        return new LogicalQueryPredicate<>(schema.identity(), Objects.requireNonNull(expression));
    }

    static <Q> QueryPredicate<Q> alwaysTrue(QuerySchema<Q> schema) {
        return from(schema, new QueryExpression.Literal(true));
    }

    static <Q> QueryPredicate<Q> alwaysFalse(QuerySchema<Q> schema) {
        return from(schema, new QueryExpression.Literal(false));
    }

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
