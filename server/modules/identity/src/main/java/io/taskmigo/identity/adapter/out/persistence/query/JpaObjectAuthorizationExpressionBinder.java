package io.taskmigo.identity.adapter.out.persistence.query;

import io.taskmigo.authorization.object.model.ObjectAuthorizationExpression;
import io.taskmigo.jpaquery.JpaQueryExpressionBinder;
import io.taskmigo.jpaquery.QuerySchema;
import io.taskmigo.query.model.QueryExpression;
import org.springframework.data.jpa.domain.Specification;

/// Adapts the approved Object Authorization expression subset to the shared JPA query compiler.
final class JpaObjectAuthorizationExpressionBinder {

    private JpaObjectAuthorizationExpressionBinder() {}

    static <E> Specification<E> bind(ObjectAuthorizationExpression expression, QuerySchema<E> schema) {
        return JpaQueryExpressionBinder.bind(queryExpression(expression), schema);
    }

    private static QueryExpression queryExpression(ObjectAuthorizationExpression expression) {
        return switch (expression) {
            case ObjectAuthorizationExpression.Literal literal -> new QueryExpression.Literal(literal.value());
            case ObjectAuthorizationExpression.Reference reference -> new QueryExpression.Reference(
                reference.root(),
                reference.path()
            );
            case ObjectAuthorizationExpression.Unary unary when (
                unary.operator() == ObjectAuthorizationExpression.UnaryOperator.NOT
            ) -> new QueryExpression.Unary(QueryExpression.UnaryOperator.NOT, queryExpression(unary.operand()));
            case ObjectAuthorizationExpression.Binary binary -> new QueryExpression.Binary(
                operator(binary.operator()),
                queryExpression(binary.left()),
                queryExpression(binary.right())
            );
            default -> throw new IllegalArgumentException("Unsupported Object Authorization expression for JPA binding");
        };
    }

    private static QueryExpression.BinaryOperator operator(ObjectAuthorizationExpression.BinaryOperator operator) {
        return switch (operator) {
            case OR -> QueryExpression.BinaryOperator.OR;
            case AND -> QueryExpression.BinaryOperator.AND;
            case EQUAL -> QueryExpression.BinaryOperator.EQUAL;
            case NOT_EQUAL -> QueryExpression.BinaryOperator.NOT_EQUAL;
            case GREATER -> QueryExpression.BinaryOperator.GREATER;
            case GREATER_OR_EQUAL -> QueryExpression.BinaryOperator.GREATER_OR_EQUAL;
            case LESS -> QueryExpression.BinaryOperator.LESS;
            case LESS_OR_EQUAL -> QueryExpression.BinaryOperator.LESS_OR_EQUAL;
            case CONTAINS -> QueryExpression.BinaryOperator.CONTAINS;
            case IN, ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> throw new IllegalArgumentException(
                "Unsupported Object Authorization operator for JPA binding: " + operator
            );
        };
    }
}
