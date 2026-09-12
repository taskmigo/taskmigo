package io.taskmigo.language;

import io.taskmigo.language.ast.BinaryOperator;
import io.taskmigo.language.ast.ExpressionVisitor;
import io.taskmigo.language.ast.QuantifierOperator;
import io.taskmigo.language.ast.UnaryOperator;
import java.util.ArrayList;
import java.util.List;

/// Bridges the internal Semantic AST to the stable visitor-only translation surface.
final class SemanticExpressionMapper {

    private SemanticExpressionMapper() {}

    static <R> R map(SemanticAst.Expression expression, ExpressionVisitor<R> visitor) {
        return switch (expression) {
            case SemanticAst.Literal literal -> visitor.literal(literal.value(), literal.type(), literal.span());
            case SemanticAst.Reference reference -> visitor.reference(
                reference.root(),
                reference.path(),
                reference.type(),
                reference.nullable(),
                reference.symbolic(),
                reference.span()
            );
            case SemanticAst.ListLiteral list -> visitor.list(
                mapList(list.values(), visitor),
                list.type(),
                list.span()
            );
            case SemanticAst.Unary unary -> visitor.unary(
                UnaryOperator.valueOf(unary.operator().name()),
                map(unary.operand(), visitor),
                unary.type(),
                unary.span()
            );
            case SemanticAst.Binary binary -> visitor.binary(
                BinaryOperator.valueOf(binary.operator().name()),
                map(binary.left(), visitor),
                map(binary.right(), visitor),
                binary.type(),
                binary.span()
            );
            case SemanticAst.Conditional conditional -> visitor.conditional(
                map(conditional.condition(), visitor),
                map(conditional.whenTrue(), visitor),
                map(conditional.whenFalse(), visitor),
                conditional.type(),
                conditional.nullable(),
                conditional.span()
            );
            case SemanticAst.Quantifier quantifier -> visitor.quantifier(
                QuantifierOperator.valueOf(quantifier.operator().name()),
                map(quantifier.collection(), visitor),
                quantifier.elementName(),
                map(quantifier.predicate(), visitor),
                quantifier.span()
            );
            case SemanticAst.Length length -> visitor.length(map(length.operand(), visitor), length.span());
        };
    }

    private static <R> List<R> mapList(List<SemanticAst.Expression> expressions, ExpressionVisitor<R> visitor) {
        ArrayList<R> result = new ArrayList<>(expressions.size());
        for (SemanticAst.Expression expression : expressions) {
            result.add(map(expression, visitor));
        }
        return List.copyOf(result);
    }
}
