package io.taskmigo.query;

import io.taskmigo.embeddedlanguage.LanguageDiagnostic.SourceSpan;
import io.taskmigo.embeddedlanguage.LanguageType;
import io.taskmigo.embeddedlanguage.SemanticAst;
import java.util.Objects;
import java.util.Set;

/// Creates and composes opaque predicates at the logical query boundary.
@SuppressWarnings("checkstyle:NeedBraces")
public final class QueryPredicateFactory {

    private QueryPredicateFactory() {}

    /// Wraps a typed Boolean Semantic AST expression for a compatible Query Schema.
    public static <Q> QueryPredicate<Q> from(QuerySchema<Q> schema, SemanticAst.Expression expression) {
        Objects.requireNonNull(schema);
        Objects.requireNonNull(expression);
        if (expression.type() != LanguageType.Scalar.BOOL) throw new IllegalArgumentException(
            "query predicate must be Bool"
        );
        return new LogicalQueryPredicate<>(schema.identity(), expression);
    }

    /// Returns a schema-bound identity predicate.
    public static <Q> QueryPredicate<Q> alwaysTrue(QuerySchema<Q> schema) {
        return from(schema, new SemanticAst.Literal(true, LanguageType.Scalar.BOOL, Set.of(), span()));
    }

    /// Returns a schema-bound zero predicate.
    public static <Q> QueryPredicate<Q> alwaysFalse(QuerySchema<Q> schema) {
        return from(schema, new SemanticAst.Literal(false, LanguageType.Scalar.BOOL, Set.of(), span()));
    }

    /// Returns the Semantic AST expression owned by a predicate for trusted persistence adapters.
    public static SemanticAst.Expression expression(QueryPredicate<?> predicate) {
        if (!(predicate instanceof LogicalQueryPredicate<?> logical)) {
            throw new IllegalArgumentException("unsupported Query Predicate implementation");
        }
        return logical.expression();
    }

    static String schemaIdentity(QueryPredicate<?> predicate) {
        if (!(predicate instanceof LogicalQueryPredicate<?> logical)) throw new IllegalArgumentException(
            "unsupported Query Predicate implementation"
        );
        return logical.schemaIdentity();
    }

    static <Q> QueryPredicate<Q> wrap(String schemaIdentity, SemanticAst.Expression expression) {
        return new LogicalQueryPredicate<>(schemaIdentity, expression);
    }

    static <Q> QueryPredicate<Q> constantLike(QueryPredicate<?> predicate, boolean value) {
        return wrap(
            schemaIdentity(predicate),
            new SemanticAst.Literal(value, LanguageType.Scalar.BOOL, Set.of(), span())
        );
    }

    private static SourceSpan span() {
        return new SourceSpan(1, 0, 1, 0);
    }

    private record LogicalQueryPredicate<Q>(
        String schemaIdentity,
        SemanticAst.Expression expression
    ) implements QueryPredicate<Q> {
        private LogicalQueryPredicate {
            Objects.requireNonNull(schemaIdentity);
            Objects.requireNonNull(expression);
        }

        @Override
        public boolean isAlwaysTrue() {
            return expression instanceof SemanticAst.Literal literal && Boolean.TRUE.equals(literal.value());
        }

        @Override
        public boolean isAlwaysFalse() {
            return expression instanceof SemanticAst.Literal literal && Boolean.FALSE.equals(literal.value());
        }
    }
}
