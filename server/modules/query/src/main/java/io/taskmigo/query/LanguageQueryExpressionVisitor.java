package io.taskmigo.query;

import io.taskmigo.language.LanguageDiagnostic.SourceSpan;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ast.BinaryOperator;
import io.taskmigo.language.ast.ExpressionVisitor;
import io.taskmigo.language.ast.QuantifierOperator;
import io.taskmigo.language.ast.UnaryOperator;
import io.taskmigo.query.persistence.QueryExpression;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Converts Language semantics into the Query-owned persistence-neutral model once at the module boundary.
final class LanguageQueryExpressionVisitor implements ExpressionVisitor<QueryExpression> {

    static final LanguageQueryExpressionVisitor INSTANCE = new LanguageQueryExpressionVisitor();

    private LanguageQueryExpressionVisitor() {}

    @Override
    public QueryExpression literal(@Nullable Object value, LanguageType type, SourceSpan span) {
        return new QueryExpression.Literal(value);
    }

    @Override
    public QueryExpression reference(
        String root,
        List<String> path,
        LanguageType type,
        boolean nullable,
        boolean symbolic,
        SourceSpan span
    ) {
        return new QueryExpression.Reference(root, path);
    }

    @Override
    public QueryExpression list(List<QueryExpression> values, LanguageType type, SourceSpan span) {
        return new QueryExpression.ListValue(values);
    }

    @Override
    public QueryExpression unary(UnaryOperator operator, QueryExpression operand, LanguageType type, SourceSpan span) {
        return new QueryExpression.Unary(QueryExpression.UnaryOperator.valueOf(operator.name()), operand);
    }

    @Override
    public QueryExpression binary(
        BinaryOperator operator,
        QueryExpression left,
        QueryExpression right,
        LanguageType type,
        SourceSpan span
    ) {
        return new QueryExpression.Binary(QueryExpression.BinaryOperator.valueOf(operator.name()), left, right);
    }

    @Override
    public QueryExpression conditional(
        QueryExpression condition,
        QueryExpression whenTrue,
        QueryExpression whenFalse,
        LanguageType type,
        boolean nullable,
        SourceSpan span
    ) {
        return new QueryExpression.Conditional(condition, whenTrue, whenFalse);
    }

    @Override
    public QueryExpression quantifier(
        QuantifierOperator operator,
        QueryExpression collection,
        String elementName,
        QueryExpression predicate,
        SourceSpan span
    ) {
        return new QueryExpression.Quantifier(
            QueryExpression.QuantifierOperator.valueOf(operator.name()),
            collection,
            elementName,
            predicate
        );
    }

    @Override
    public QueryExpression length(QueryExpression operand, SourceSpan span) {
        return new QueryExpression.Length(operand);
    }
}
