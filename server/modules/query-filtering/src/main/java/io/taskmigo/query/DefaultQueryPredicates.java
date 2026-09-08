package io.taskmigo.query;

import io.taskmigo.embeddedlanguage.LanguageDiagnostic;
import io.taskmigo.embeddedlanguage.LanguageDiagnostic.SourceSpan;
import io.taskmigo.embeddedlanguage.LanguageType;
import io.taskmigo.embeddedlanguage.SemanticAst;
import java.util.Set;

/// Default Boolean-algebra implementation for opaque Query Predicates.
@SuppressWarnings("checkstyle:NeedBraces")
final class DefaultQueryPredicates implements QueryPredicates {
    static final DefaultQueryPredicates INSTANCE = new DefaultQueryPredicates();

    private DefaultQueryPredicates() {}

    @Override
    public <Q> QueryPredicate<Q> alwaysTrue() {
        return QueryPredicateFactory.wrap("", new SemanticAst.Literal(true, LanguageType.Scalar.BOOL, Set.of(), span()));
    }

    @Override
    public <Q> QueryPredicate<Q> alwaysFalse() {
        return QueryPredicateFactory.wrap("", new SemanticAst.Literal(false, LanguageType.Scalar.BOOL, Set.of(), span()));
    }

    @Override
    public <Q> QueryPredicate<Q> and(QueryPredicate<Q> left, QueryPredicate<Q> right) {
        requireCompatible(left, right);
        if (left.isAlwaysFalse()) return left;
        if (right.isAlwaysFalse()) return right;
        if (left.isAlwaysTrue()) return right;
        if (right.isAlwaysTrue()) return left;
        return QueryPredicateFactory.wrap(schema(left), binary(SemanticAst.BinaryOperator.AND, left, right));
    }

    @Override
    public <Q> QueryPredicate<Q> or(QueryPredicate<Q> left, QueryPredicate<Q> right) {
        requireCompatible(left, right);
        if (left.isAlwaysTrue()) return left;
        if (right.isAlwaysTrue()) return right;
        if (left.isAlwaysFalse()) return right;
        if (right.isAlwaysFalse()) return left;
        return QueryPredicateFactory.wrap(schema(left), binary(SemanticAst.BinaryOperator.OR, left, right));
    }

    @Override
    public <Q> QueryPredicate<Q> not(QueryPredicate<Q> predicate) {
        if (predicate.isAlwaysTrue()) return alwaysFalse();
        if (predicate.isAlwaysFalse()) return alwaysTrue();
        SemanticAst.Expression expression = QueryPredicateFactory.expression(predicate);
        return QueryPredicateFactory.wrap(schema(predicate), new SemanticAst.Unary(
            SemanticAst.UnaryOperator.NOT, expression, LanguageType.Scalar.BOOL, expression.dependencies(), span()
        ));
    }

    private static <Q> SemanticAst.Expression binary(SemanticAst.BinaryOperator operator, QueryPredicate<Q> left, QueryPredicate<Q> right) {
        SemanticAst.Expression l = QueryPredicateFactory.expression(left);
        SemanticAst.Expression r = QueryPredicateFactory.expression(right);
        Set<String> dependencies = java.util.stream.Stream.of(l, r)
            .flatMap(expression -> expression.dependencies().stream()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new SemanticAst.Binary(operator, l, r, LanguageType.Scalar.BOOL, dependencies, span());
    }

    private static String schema(QueryPredicate<?> predicate) {
        return QueryPredicateFactory.schemaIdentity(predicate);
    }

    private static void requireCompatible(QueryPredicate<?> left, QueryPredicate<?> right) {
        if (!schema(left).equals(schema(right)) && !schema(left).isEmpty() && !schema(right).isEmpty()) {
            throw new IllegalArgumentException("Query Predicates belong to incompatible schemas");
        }
    }

    private static LanguageDiagnostic.SourceSpan span() {
        return new SourceSpan(1, 0, 1, 0);
    }
}
