package io.taskmigo.auth.authorization.object;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.embeddedlanguage.SemanticAst;

/// Validates symbolic Object Authorization paths and operators against one logical schema.
@SuppressWarnings("checkstyle:NeedBraces")
final class ObjectAuthorizationSchemaValidator {

    private ObjectAuthorizationSchemaValidator() {}

    static <Q> void validate(SemanticAst.Expression expression, ObjectAuthorizationSchema<Q> schema) {
        switch (expression) {
            case SemanticAst.Literal _ -> {
            }
            case SemanticAst.Reference reference -> validateReference(reference, schema);
            case SemanticAst.ListLiteral list -> list.values().forEach(value -> validate(value, schema));
            case SemanticAst.Unary unary -> {
                validate(unary.operand(), schema);
                if (unary.operand() instanceof SemanticAst.Reference reference && reference.root().equals("object")) {
                    requireOperator(
                        reference,
                        unary.operator() == SemanticAst.UnaryOperator.MINUS
                            ? ObjectAuthorizationOperator.NOT
                            : ObjectAuthorizationOperator.NOT,
                        schema
                    );
                }
            }
            case SemanticAst.Length length -> {
                if (length.operand() instanceof SemanticAst.Reference reference && reference.root().equals("object")) {
                    requireOperator(reference, ObjectAuthorizationOperator.LENGTH, schema);
                }
                validate(length.operand(), schema);
            }
            case SemanticAst.Binary binary -> {
                ObjectAuthorizationOperator operator = operator(binary.operator());
                requireOperator(binary.left(), operator, schema);
                requireOperator(binary.right(), operator, schema);
                validate(binary.left(), schema);
                validate(binary.right(), schema);
            }
            case SemanticAst.Conditional conditional -> {
                validate(conditional.condition(), schema);
                validate(conditional.whenTrue(), schema);
                validate(conditional.whenFalse(), schema);
            }
            case SemanticAst.Quantifier quantifier -> {
                ObjectAuthorizationOperator operator = switch (quantifier.operator()) {
                    case ALL -> ObjectAuthorizationOperator.ALL;
                    case ANY -> ObjectAuthorizationOperator.ANY;
                    case NONE -> ObjectAuthorizationOperator.NONE;
                };
                requireOperator(quantifier.collection(), operator, schema);
                validate(quantifier.collection(), schema);
                validate(quantifier.predicate(), schema);
            }
        }
    }

    private static <Q> void validateReference(SemanticAst.Reference reference, ObjectAuthorizationSchema<Q> schema) {
        if (reference.root().equals("object")) {
            schema
                .field(new ObjectAuthorizationPath(reference.path()))
                .orElseThrow(() -> invalid("object path is not queryable"));
        }
    }

    private static <Q> void requireOperator(
        SemanticAst.Expression expression,
        ObjectAuthorizationOperator operator,
        ObjectAuthorizationSchema<Q> schema
    ) {
        if (expression instanceof SemanticAst.Reference reference && reference.root().equals("object")) {
            if (operator == ObjectAuthorizationOperator.AND || operator == ObjectAuthorizationOperator.OR) return;
            requireOperator(reference, operator, schema);
        }
    }

    private static <Q> void requireOperator(
        SemanticAst.Reference reference,
        ObjectAuthorizationOperator operator,
        ObjectAuthorizationSchema<Q> schema
    ) {
        ObjectAuthorizationField field = schema
            .field(new ObjectAuthorizationPath(reference.path()))
            .orElseThrow(() -> invalid("object path is not queryable"));
        if (!field.operators().contains(operator)) throw invalid(
            "operator is not supported for object path " + field.path().text()
        );
    }

    private static ObjectAuthorizationOperator operator(SemanticAst.BinaryOperator operator) {
        return switch (operator) {
            case AND -> ObjectAuthorizationOperator.AND;
            case OR -> ObjectAuthorizationOperator.OR;
            case EQUAL -> ObjectAuthorizationOperator.EQ;
            case NOT_EQUAL -> ObjectAuthorizationOperator.NE;
            case GREATER -> ObjectAuthorizationOperator.GT;
            case GREATER_OR_EQUAL -> ObjectAuthorizationOperator.GE;
            case LESS -> ObjectAuthorizationOperator.LT;
            case LESS_OR_EQUAL -> ObjectAuthorizationOperator.LE;
            case ADD -> ObjectAuthorizationOperator.ADD;
            case SUBTRACT -> ObjectAuthorizationOperator.SUBTRACT;
            case MULTIPLY -> ObjectAuthorizationOperator.MULTIPLY;
            case DIVIDE -> ObjectAuthorizationOperator.DIVIDE;
            case MODULO -> ObjectAuthorizationOperator.MODULO;
            case IN -> ObjectAuthorizationOperator.IN;
        };
    }

    private static AuthorizationException invalid(String message) {
        return new AuthorizationException("Invalid Object authorization policy: " + message);
    }
}
