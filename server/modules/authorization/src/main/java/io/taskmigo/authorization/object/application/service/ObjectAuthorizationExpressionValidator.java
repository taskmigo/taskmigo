package io.taskmigo.authorization.object.application.service;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;
import io.taskmigo.query.QuerySchemaView;

/// Validates Object Authorization object paths and operators against the operation query schema.
final class ObjectAuthorizationExpressionValidator {

    private ObjectAuthorizationExpressionValidator() {}

    static void validate(ObjectAuthorizationExpression expression, QuerySchemaView schema) {
        switch (expression) {
            case ObjectAuthorizationExpression.Literal _ -> {
            }
            case ObjectAuthorizationExpression.Reference reference -> validateReference(reference, schema);
            case ObjectAuthorizationExpression.Binary binary -> validateBinary(binary, schema);
            default -> throw invalid("unsupported object authorization expression");
        }
    }

    private static void validateBinary(ObjectAuthorizationExpression.Binary binary, QuerySchemaView schema) {
        switch (binary.operator()) {
            case AND, OR -> {
                validate(binary.left(), schema);
                validate(binary.right(), schema);
            }
            case EQUAL, NOT_EQUAL, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL, CONTAINS -> {
                QueryOperator operator = operator(binary.operator());
                requireOperator(binary.left(), operator, schema);
                requireOperator(binary.right(), operator, schema);
                validateOperand(binary.left(), schema);
                validateOperand(binary.right(), schema);
            }
            case IN, ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> throw invalid(
                "unsupported object authorization operator"
            );
        }
    }

    private static void validateOperand(ObjectAuthorizationExpression expression, QuerySchemaView schema) {
        switch (expression) {
            case ObjectAuthorizationExpression.Literal _ -> {
            }
            case ObjectAuthorizationExpression.Reference reference -> validateReference(reference, schema);
            default -> throw invalid("comparison operands must be references or literals");
        }
    }

    private static void validateReference(ObjectAuthorizationExpression.Reference reference, QuerySchemaView schema) {
        if (reference.root().equals("object")) {
            schema.field(new QueryPath(reference.path())).orElseThrow(() -> invalid("object path is not queryable"));
        }
    }

    private static void requireOperator(
        ObjectAuthorizationExpression expression,
        QueryOperator operator,
        QuerySchemaView schema
    ) {
        if (
            expression instanceof ObjectAuthorizationExpression.Reference reference && reference.root().equals("object")
        ) {
            QueryFieldDescriptor field = schema
                .field(new QueryPath(reference.path()))
                .orElseThrow(() -> invalid("object path is not queryable"));
            if (!field.supports(operator)) {
                throw invalid("operator is not supported for object path " + field.path().text());
            }
        }
    }

    private static QueryOperator operator(ObjectAuthorizationExpression.BinaryOperator operator) {
        return switch (operator) {
            case AND -> QueryOperator.AND;
            case OR -> QueryOperator.OR;
            case EQUAL -> QueryOperator.EQ;
            case NOT_EQUAL -> QueryOperator.NE;
            case GREATER -> QueryOperator.GT;
            case GREATER_OR_EQUAL -> QueryOperator.GTE;
            case LESS -> QueryOperator.LT;
            case LESS_OR_EQUAL -> QueryOperator.LTE;
            case CONTAINS -> QueryOperator.CONTAINS;
            case IN, ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> throw invalid(
                "unsupported object authorization operator"
            );
        };
    }

    private static AuthorizationException invalid(String message) {
        return new AuthorizationException("Invalid Object authorization policy: " + message);
    }
}
