package io.taskmigo.language.ast;

import io.taskmigo.language.LanguageDiagnostic.SourceSpan;
import io.taskmigo.language.LanguageType;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// Translates a read-only Language expression without exposing concrete Semantic AST nodes.
public interface ExpressionVisitor<R> {
    R literal(@Nullable Object value, LanguageType type, SourceSpan span);

    R reference(
        String root,
        List<String> path,
        LanguageType type,
        boolean nullable,
        boolean symbolic,
        SourceSpan span
    );

    R list(List<R> values, LanguageType type, SourceSpan span);

    R unary(UnaryOperator operator, R operand, LanguageType type, SourceSpan span);

    R binary(BinaryOperator operator, R left, R right, LanguageType type, SourceSpan span);

    R conditional(R condition, R whenTrue, R whenFalse, LanguageType type, boolean nullable, SourceSpan span);

    R quantifier(QuantifierOperator operator, R collection, String elementName, R predicate, SourceSpan span);

    R length(R operand, SourceSpan span);
}
