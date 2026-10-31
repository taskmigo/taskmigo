package io.taskmigo.authorization.object.application.service;

import io.taskmigo.authorization.core.AuthorizationException;
import io.taskmigo.authorization.object.ObjectAuthorizationSchema;
import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.query.QueryFieldContext;
import io.taskmigo.query.QueryFieldDescriptor;
import io.taskmigo.query.QueryOperator;
import io.taskmigo.query.QueryPath;

/// Validates Object Authorization object paths and operators against the selected authorization schema.
final class ObjectAuthorizationExpressionValidator {

    private ObjectAuthorizationExpressionValidator() {}

    static void validate(ObjectAuthorizationExpression expression, ObjectAuthorizationSchema<?> schema) {
        validate(expression, schema, QueryFieldContext.empty());
    }

    static void validate(
        ObjectAuthorizationExpression expression,
        ObjectAuthorizationSchema<?> schema,
        QueryFieldContext queryContext
    ) {
        switch (expression) {
            case ObjectAuthorizationExpression.Literal _ -> {
            }
            case ObjectAuthorizationExpression.Reference reference -> validateReference(
                reference,
                schema,
                queryContext
            );
            case ObjectAuthorizationExpression.Binary binary -> validateBinary(binary, schema, queryContext);
            default -> throw invalid("unsupported object authorization expression");
        }
    }

    private static void validateBinary(
        ObjectAuthorizationExpression.Binary binary,
        ObjectAuthorizationSchema<?> schema,
        QueryFieldContext queryContext
    ) {
        switch (binary.operator()) {
            case AND, OR -> {
                validate(binary.left(), schema, queryContext);
                validate(binary.right(), schema, queryContext);
            }
            case EQUAL, NOT_EQUAL, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL, CONTAINS -> {
                QueryOperator operator = operator(binary.operator());
                requireOperator(binary.left(), operator, schema, queryContext);
                requireOperator(binary.right(), operator, schema, queryContext);
                validateOperand(binary.left(), schema, queryContext);
                validateOperand(binary.right(), schema, queryContext);
            }
            case IN, ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> throw invalid(
                "unsupported object authorization operator"
            );
        }
    }

    private static void validateOperand(
        ObjectAuthorizationExpression expression,
        ObjectAuthorizationSchema<?> schema,
        QueryFieldContext queryContext
    ) {
        switch (expression) {
            case ObjectAuthorizationExpression.Literal _ -> {
            }
            case ObjectAuthorizationExpression.Reference reference -> validateReference(
                reference,
                schema,
                queryContext
            );
            default -> throw invalid("comparison operands must be references or literals");
        }
    }

    private static void validateReference(
        ObjectAuthorizationExpression.Reference reference,
        ObjectAuthorizationSchema<?> schema,
        QueryFieldContext queryContext
    ) {
        if (reference.root().equals("object")) {
            schema
                .field(new QueryPath(reference.path()), queryContext)
                .orElseThrow(() -> invalid("object path is not authorized by this contract"));
        }
    }

    private static void requireOperator(
        ObjectAuthorizationExpression expression,
        QueryOperator operator,
        ObjectAuthorizationSchema<?> schema,
        QueryFieldContext queryContext
    ) {
        if (
            expression instanceof ObjectAuthorizationExpression.Reference reference && reference.root().equals("object")
        ) {
            QueryFieldDescriptor field = schema
                .field(new QueryPath(reference.path()), queryContext)
                .orElseThrow(() -> invalid("object path is not authorized by this contract"));
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
