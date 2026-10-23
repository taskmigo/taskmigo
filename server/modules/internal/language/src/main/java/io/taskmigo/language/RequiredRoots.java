package io.taskmigo.language;

import java.util.LinkedHashSet;
import java.util.Set;

/// Derives concrete runtime roots from the final folded Semantic AST.
final class RequiredRoots {

    private RequiredRoots() {}

    static Set<String> from(SemanticAst.Expression expression) {
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        collect(expression, roots);
        return Set.copyOf(roots);
    }

    private static void collect(SemanticAst.Expression expression, Set<String> roots) {
        switch (expression) {
            case SemanticAst.Literal _ -> {
            }
            case SemanticAst.Reference reference -> {
                if (!reference.symbolic() && !reference.root().equals("__lambda__")) {
                    roots.add(reference.root());
                }
            }
            case SemanticAst.ListLiteral list -> list.values().forEach(value -> collect(value, roots));
            case SemanticAst.Unary unary -> collect(unary.operand(), roots);
            case SemanticAst.Binary binary -> {
                collect(binary.left(), roots);
                collect(binary.right(), roots);
            }
            case SemanticAst.Conditional conditional -> {
                collect(conditional.condition(), roots);
                collect(conditional.whenTrue(), roots);
                collect(conditional.whenFalse(), roots);
            }
            case SemanticAst.Quantifier quantifier -> {
                collect(quantifier.collection(), roots);
                collect(quantifier.predicate(), roots);
            }
            case SemanticAst.Length length -> collect(length.operand(), roots);
        }
    }
}
