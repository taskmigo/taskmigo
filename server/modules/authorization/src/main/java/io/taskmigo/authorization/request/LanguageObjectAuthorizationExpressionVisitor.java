package io.taskmigo.authorization.request;

import io.taskmigo.authorization.object.persistence.ObjectAuthorizationExpression;
import io.taskmigo.language.LanguageDiagnostic.SourceSpan;
import io.taskmigo.language.LanguageType;
import io.taskmigo.language.ast.BinaryOperator;
import io.taskmigo.language.ast.ExpressionVisitor;
import io.taskmigo.language.ast.QuantifierOperator;
import io.taskmigo.language.ast.UnaryOperator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Converts Language semantics into the Object Authorization-owned persistence model at the module boundary.
final class LanguageObjectAuthorizationExpressionVisitor implements ExpressionVisitor<ObjectAuthorizationExpression> {

    static final LanguageObjectAuthorizationExpressionVisitor INSTANCE =
        new LanguageObjectAuthorizationExpressionVisitor();

    private LanguageObjectAuthorizationExpressionVisitor() {}

    @Override
    public ObjectAuthorizationExpression literal(@Nullable Object value, LanguageType type, SourceSpan span) {
        return new ObjectAuthorizationExpression.Literal(value);
    }

    @Override
    public ObjectAuthorizationExpression reference(
        String root,
        List<String> path,
        LanguageType type,
        boolean nullable,
        boolean symbolic,
        SourceSpan span
    ) {
        return new ObjectAuthorizationExpression.Reference(root, path);
    }

    @Override
    public ObjectAuthorizationExpression list(
        List<ObjectAuthorizationExpression> values,
        LanguageType type,
        SourceSpan span
    ) {
        return new ObjectAuthorizationExpression.ListValue(values);
    }

    @Override
    public ObjectAuthorizationExpression unary(
        UnaryOperator operator,
        ObjectAuthorizationExpression operand,
        LanguageType type,
        SourceSpan span
    ) {
        return new ObjectAuthorizationExpression.Unary(
            ObjectAuthorizationExpression.UnaryOperator.valueOf(operator.name()),
            operand
        );
    }

    @Override
    public ObjectAuthorizationExpression binary(
        BinaryOperator operator,
        ObjectAuthorizationExpression left,
        ObjectAuthorizationExpression right,
        LanguageType type,
        SourceSpan span
    ) {
        return new ObjectAuthorizationExpression.Binary(
            ObjectAuthorizationExpression.BinaryOperator.valueOf(operator.name()),
            left,
            right
        );
    }

    @Override
    public ObjectAuthorizationExpression conditional(
        ObjectAuthorizationExpression condition,
        ObjectAuthorizationExpression whenTrue,
        ObjectAuthorizationExpression whenFalse,
        LanguageType type,
        boolean nullable,
        SourceSpan span
    ) {
        return new ObjectAuthorizationExpression.Conditional(condition, whenTrue, whenFalse);
    }

    @Override
    public ObjectAuthorizationExpression quantifier(
        QuantifierOperator operator,
        ObjectAuthorizationExpression collection,
        String elementName,
        ObjectAuthorizationExpression predicate,
        SourceSpan span
    ) {
        return new ObjectAuthorizationExpression.Quantifier(
            ObjectAuthorizationExpression.QuantifierOperator.valueOf(operator.name()),
            collection,
            elementName,
            predicate
        );
    }

    @Override
    public ObjectAuthorizationExpression length(ObjectAuthorizationExpression operand, SourceSpan span) {
        return new ObjectAuthorizationExpression.Length(operand);
    }
}
