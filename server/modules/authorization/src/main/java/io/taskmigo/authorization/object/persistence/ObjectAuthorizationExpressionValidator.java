package io.taskmigo.authorization.object.persistence;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationField;
import io.taskmigo.authorization.object.ObjectAuthorizationOperator;
import io.taskmigo.authorization.object.ObjectAuthorizationPath;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;

/// Validates Object Authorization expression paths and operators against one logical schema.
@SuppressWarnings("checkstyle:NeedBraces")
public final class ObjectAuthorizationExpressionValidator {

    private ObjectAuthorizationExpressionValidator() {}

    public static <Q> void validate(ObjectAuthorizationExpression expression, ObjectAuthorizationSchema<Q> schema) {
        switch (expression) {
            case ObjectAuthorizationExpression.Literal _ -> {
            }
            case ObjectAuthorizationExpression.Reference reference -> validateReference(reference, schema);
            case ObjectAuthorizationExpression.ListValue list -> list.values().forEach(value ->
                validate(value, schema)
            );
            case ObjectAuthorizationExpression.Unary unary -> {
                validate(unary.operand(), schema);
                if (
                    unary.operand() instanceof ObjectAuthorizationExpression.Reference reference &&
                    reference.root().equals("object")
                ) requireOperator(reference, ObjectAuthorizationOperator.NOT, schema);
            }
            case ObjectAuthorizationExpression.Length length -> {
                if (
                    length.operand() instanceof ObjectAuthorizationExpression.Reference reference &&
                    reference.root().equals("object")
                ) requireOperator(reference, ObjectAuthorizationOperator.LENGTH, schema);
                validate(length.operand(), schema);
            }
            case ObjectAuthorizationExpression.Binary binary -> {
                ObjectAuthorizationOperator operator = operator(binary.operator());
                requireOperator(binary.left(), operator, schema);
                requireOperator(binary.right(), operator, schema);
                validate(binary.left(), schema);
                validate(binary.right(), schema);
            }
            case ObjectAuthorizationExpression.Conditional conditional -> {
                validate(conditional.condition(), schema);
                validate(conditional.whenTrue(), schema);
                validate(conditional.whenFalse(), schema);
            }
            case ObjectAuthorizationExpression.Quantifier quantifier -> {
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

    private static <Q> void validateReference(
        ObjectAuthorizationExpression.Reference reference,
        ObjectAuthorizationSchema<Q> schema
    ) {
        if (reference.root().equals("object")) {
            schema
                .field(new ObjectAuthorizationPath(reference.path()))
                .orElseThrow(() -> invalid("object path is not queryable"));
        }
    }

    private static <Q> void requireOperator(
        ObjectAuthorizationExpression expression,
        ObjectAuthorizationOperator operator,
        ObjectAuthorizationSchema<Q> schema
    ) {
        if (
            expression instanceof ObjectAuthorizationExpression.Reference reference && reference.root().equals("object")
        ) {
            if (operator == ObjectAuthorizationOperator.AND || operator == ObjectAuthorizationOperator.OR) return;
            requireOperator(reference, operator, schema);
        }
    }

    private static <Q> void requireOperator(
        ObjectAuthorizationExpression.Reference reference,
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

    private static ObjectAuthorizationOperator operator(ObjectAuthorizationExpression.BinaryOperator operator) {
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
