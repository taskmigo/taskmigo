package io.taskmigo.query;

import io.taskmigo.language.FieldId;
import io.taskmigo.query.model.QueryExpression;
import org.jspecify.annotations.Nullable;

/// Validates execution capabilities for already-resolved semantic query expressions.
final class QueryBindingValidator {

    private QueryBindingValidator() {}

    static void validate(QueryExpression expression, QueryBinding<?> binding) {
        switch (expression) {
            case QueryExpression.Literal _ -> {
            }
            case QueryExpression.Reference reference -> requireField(reference.fieldId(), binding);
            case QueryExpression.ListValue list -> list.values().forEach(value -> validate(value, binding));
            case QueryExpression.Unary unary -> {
                requireOperator(unary.operand(), operator(unary.operator()), binding);
                validate(unary.operand(), binding);
            }
            case QueryExpression.Length length -> {
                requireOperator(length.operand(), QueryOperator.LENGTH, binding);
                validate(length.operand(), binding);
            }
            case QueryExpression.Binary binary -> {
                QueryOperator operator = operator(binary.operator());
                requireOperator(binary.left(), operator, binding);
                requireOperator(binary.right(), operator, binding);
                validate(binary.left(), binding);
                validate(binary.right(), binding);
            }
            case QueryExpression.Conditional conditional -> {
                validate(conditional.condition(), binding);
                validate(conditional.whenTrue(), binding);
                validate(conditional.whenFalse(), binding);
            }
            case QueryExpression.Quantifier quantifier -> {
                QueryOperator operator = switch (quantifier.operator()) {
                    case ALL -> QueryOperator.ALL;
                    case ANY -> QueryOperator.ANY;
                    case NONE -> QueryOperator.NONE;
                };
                requireOperator(quantifier.collection(), operator, binding);
                validate(quantifier.collection(), binding);
                validate(quantifier.predicate(), binding);
            }
        }
    }

    private static void requireField(@Nullable FieldId id, QueryBinding<?> binding) {
        if (id != null) {
            binding.field(id).orElseThrow(() -> invalid("query binding does not declare field " + id.value()));
        }
    }

    private static void requireOperator(
        QueryExpression expression,
        QueryOperator operator,
        QueryBinding<?> binding
    ) {
        if (operator == QueryOperator.AND || operator == QueryOperator.OR) {
            return;
        }
        switch (expression) {
            case QueryExpression.Literal _ -> {
            }
            case QueryExpression.Reference reference -> {
                FieldId fieldId = reference.fieldId();
                if (fieldId != null) {
                    QueryFieldBinding field = binding
                        .field(fieldId)
                        .orElseThrow(() -> invalid("query binding does not declare field " + fieldId.value()));
                    if (!field.operators().contains(operator)) {
                        throw invalid("operator is not supported for field " + field.id().value());
                    }
                }
            }
            case QueryExpression.ListValue list -> list.values().forEach(value -> requireOperator(value, operator, binding));
            case QueryExpression.Unary unary -> requireOperator(unary.operand(), operator, binding);
            case QueryExpression.Length length -> requireOperator(length.operand(), operator, binding);
            case QueryExpression.Binary binary -> {
                requireOperator(binary.left(), operator, binding);
                requireOperator(binary.right(), operator, binding);
            }
            case QueryExpression.Conditional conditional -> {
                requireOperator(conditional.condition(), operator, binding);
                requireOperator(conditional.whenTrue(), operator, binding);
                requireOperator(conditional.whenFalse(), operator, binding);
            }
            case QueryExpression.Quantifier quantifier -> {
                requireOperator(quantifier.collection(), operator, binding);
                requireOperator(quantifier.predicate(), operator, binding);
            }
        }
    }

    private static QueryOperator operator(QueryExpression.UnaryOperator operator) {
        return switch (operator) {
            case NOT -> QueryOperator.NOT;
            case PLUS -> QueryOperator.PLUS;
            case MINUS -> QueryOperator.MINUS;
        };
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
