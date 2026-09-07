package io.taskmigo.auth.authorization.embeddedlanguage;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.filter.FilterAst;
import io.taskmigo.auth.authorization.object.FilterSchema;
import io.taskmigo.embeddedlanguage.SemanticAst;

/// Validates the object-dependent portion of an Embedded Language Semantic AST against an Authorization Filter Schema.
public final class AuthorizationSemanticAstQueryability {

    private AuthorizationSemanticAstQueryability() {}

    /// Validates fields and operators that can remain after object-policy specialization.
    public static void validate(SemanticAst program, FilterSchema schema) {
        validate(program.expression(), schema);
    }

    private static void validate(SemanticAst.Expression expression, FilterSchema schema) {
        if (!expression.dependencies().contains("object")) return;
        switch (expression) {
            case SemanticAst.Literal _ -> {
            }
            case SemanticAst.Reference reference -> validateReference(reference, schema);
            case SemanticAst.ListLiteral _ -> throw invalid("list expressions are not supported by Filter AST");
            case SemanticAst.Unary unary -> {
                FilterAst.Operator operator = switch (unary.operator()) {
                    case NOT -> FilterAst.Operator.NOT;
                    case MINUS -> FilterAst.Operator.NEGATE;
                    case PLUS -> throw invalid("unary plus is not supported by Filter AST");
                };
                requireOperator(operator, schema);
                validate(unary.operand(), schema);
            }
            case SemanticAst.Binary binary -> {
                FilterAst.Operator operator = switch (binary.operator()) {
                    case AND -> FilterAst.Operator.AND;
                    case OR -> FilterAst.Operator.OR;
                    case EQUAL -> FilterAst.Operator.EQ;
                    case NOT_EQUAL -> FilterAst.Operator.NE;
                    case GREATER -> FilterAst.Operator.GT;
                    case GREATER_OR_EQUAL -> FilterAst.Operator.GE;
                    case LESS -> FilterAst.Operator.LT;
                    case LESS_OR_EQUAL -> FilterAst.Operator.LE;
                    case ADD -> FilterAst.Operator.ADD;
                    case SUBTRACT -> FilterAst.Operator.SUBTRACT;
                    case MULTIPLY -> FilterAst.Operator.MULTIPLY;
                    case DIVIDE -> FilterAst.Operator.DIVIDE;
                    case IN, MODULO -> throw invalid(
                        "operator is not supported by Filter AST: " + binary.operator().name().toLowerCase()
                    );
                };
                requireOperator(operator, schema);
                validate(binary.left(), schema);
                validate(binary.right(), schema);
            }
            case SemanticAst.Conditional conditional -> {
                if (conditional.condition().dependencies().contains("object")) {
                    requireOperator(FilterAst.Operator.AND, schema);
                    requireOperator(FilterAst.Operator.OR, schema);
                    requireOperator(FilterAst.Operator.NOT, schema);
                }
                validate(conditional.condition(), schema);
                validate(conditional.whenTrue(), schema);
                validate(conditional.whenFalse(), schema);
            }
        }
    }

    private static void validateReference(SemanticAst.Reference reference, FilterSchema schema) {
        if (!reference.root().equals("object") || reference.path().size() != 1) {
            throw invalid("only direct object fields are queryable");
        }
        String field = reference.path().getFirst();
        if (!schema.fields().containsKey(field)) throw invalid("object field is not queryable: " + field);
    }

    private static void requireOperator(FilterAst.Operator operator, FilterSchema schema) {
        if (!schema.operators().contains(operator)) {
            throw invalid("Filter Schema does not support operator: " + operator);
        }
    }

    private static AuthorizationException invalid(String message) {
        return new AuthorizationException("Invalid Object authorization policy: " + message);
    }
}
