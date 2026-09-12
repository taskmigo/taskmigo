package io.taskmigo.query;

import io.taskmigo.query.persistence.QueryExpression;

/// Validates that symbolic object paths and operators belong to an explicit Query Schema.
@SuppressWarnings("checkstyle:UnusedLocalVariable")
final class QuerySchemaValidator {

    private QuerySchemaValidator() {}

    static <Q> void validate(QueryExpression expression, QuerySchema<Q> schema) {
        switch (expression) {
            case QueryExpression.Literal ignored -> {
            }
            case QueryExpression.Reference reference -> validateReference(reference, schema);
            case QueryExpression.ListValue list -> list.values().forEach(value -> validate(value, schema));
            case QueryExpression.Unary unary -> validate(unary.operand(), schema);
            case QueryExpression.Length length -> {
                requireOperator(length.operand(), QueryOperator.LENGTH, schema);
                validate(length.operand(), schema);
            }
            case QueryExpression.Binary binary -> {
                QueryOperator operator = operator(binary.operator());
                requireOperator(binary.left(), operator, schema);
                requireOperator(binary.right(), operator, schema);
                validate(binary.left(), schema);
                validate(binary.right(), schema);
            }
            case QueryExpression.Conditional conditional -> {
                validate(conditional.condition(), schema);
                validate(conditional.whenTrue(), schema);
                validate(conditional.whenFalse(), schema);
            }
            case QueryExpression.Quantifier quantifier -> {
                QueryOperator operator = switch (quantifier.operator()) {
                    case ALL -> QueryOperator.ALL;
                    case ANY -> QueryOperator.ANY;
                    case NONE -> QueryOperator.NONE;
                };
                requireOperator(quantifier.collection(), operator, schema);
                validate(quantifier.collection(), schema);
                validate(quantifier.predicate(), schema);
            }
        }
    }

    private static <Q> void validateReference(QueryExpression.Reference reference, QuerySchema<Q> schema) {
        if (reference.root().equals("object")) {
            schema.field(new QueryPath(reference.path())).orElseThrow(() -> invalid("unknown query path"));
        }
    }

    private static <Q> void requireOperator(QueryExpression expression, QueryOperator operator, QuerySchema<Q> schema) {
        if (expression instanceof QueryExpression.Reference reference && reference.root().equals("object")) {
            if (operator == QueryOperator.AND || operator == QueryOperator.OR) {
                return;
            }
            QueryField field = schema
                .field(new QueryPath(reference.path()))
                .orElseThrow(() -> invalid("unknown query path"));
            if (!field.operators().contains(operator)) {
                throw invalid("operator is not supported for query path " + field.path().text());
            }
        }
    }

    private static QueryOperator operator(QueryExpression.BinaryOperator operator) {
        return switch (operator) {
            case AND -> QueryOperator.AND;
            case OR -> QueryOperator.OR;
            case EQUAL -> QueryOperator.EQ;
            case NOT_EQUAL -> QueryOperator.NE;
            case GREATER -> QueryOperator.GT;
            case GREATER_OR_EQUAL -> QueryOperator.GE;
            case LESS -> QueryOperator.LT;
            case LESS_OR_EQUAL -> QueryOperator.LE;
            case ADD -> QueryOperator.ADD;
            case SUBTRACT -> QueryOperator.SUBTRACT;
            case MULTIPLY -> QueryOperator.MULTIPLY;
            case DIVIDE -> QueryOperator.DIVIDE;
            case MODULO -> QueryOperator.MODULO;
            case IN -> QueryOperator.IN;
        };
    }

    private static FilterByException invalid(String message) {
        return new FilterByException(message);
    }
}
