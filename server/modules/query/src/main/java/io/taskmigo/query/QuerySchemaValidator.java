package io.taskmigo.query;

import io.taskmigo.foundation.TypeDescriptor;
import io.taskmigo.query.model.QueryExpression;
import java.util.Optional;

/// Validates symbolic object paths and operators against an explicit Query Schema allow-list.
final class QuerySchemaValidator {

    private QuerySchemaValidator() {}

    static <Q> void validate(QueryExpression expression, QuerySchema<Q> schema) {
        validate(expression, path ->
            schema
                .field(path)
                .map(field -> new QueryFieldDescriptor(field.path(), field.type(), field.nullable(), field.operators()))
        );
    }

    static void validate(QueryExpression expression, QuerySchemaView schema, QueryFieldContext context) {
        validate(expression, path -> schema.field(path, context));
    }

    private static void validate(QueryExpression expression, FieldResolver resolver) {
        switch (expression) {
            case QueryExpression.Literal _ -> {
            }
            case QueryExpression.Reference _ -> throw invalid(
                "filterBy object fields must be used in approved comparisons"
            );
            case QueryExpression.Binary binary -> validateBinary(binary, resolver);
            default -> throw invalid("unsupported filterBy expression");
        }
    }

    private static void validateBinary(QueryExpression.Binary binary, FieldResolver resolver) {
        switch (binary.operator()) {
            case AND, OR -> {
                validate(binary.left(), resolver);
                validate(binary.right(), resolver);
            }
            case CONTAINS -> validateContains(binary, resolver);
            case EQUAL, NOT_EQUAL, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL -> validateComparison(
                binary,
                operator(binary.operator()),
                resolver
            );
            case IN, ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> throw invalid("unsupported filterBy operator");
        }
    }

    private static void validateComparison(
        QueryExpression.Binary binary,
        QueryOperator operator,
        FieldResolver resolver
    ) {
        if (
            !(binary.left() instanceof QueryExpression.Reference reference) ||
            !reference.root().equals("object") ||
            !(binary.right() instanceof QueryExpression.Literal literal)
        ) {
            throw invalid("comparison requires an object field and a literal value");
        }
        QueryFieldDescriptor field = resolve(reference, resolver);
        requireOperator(field, operator);
        if (literal.value() == null) {
            if (operator != QueryOperator.EQ && operator != QueryOperator.NE) {
                throw invalid("null is supported only with equality operators");
            }
            if (!field.nullable()) {
                throw invalid("null comparison is not valid for non-nullable query path " + field.path().text());
            }
        } else if (isOrdering(operator) && !isOrderable(field.type())) {
            throw invalid("ordering is not supported for query path " + field.path().text());
        }
    }

    private static void validateContains(QueryExpression.Binary binary, FieldResolver resolver) {
        if (
            !(binary.left() instanceof QueryExpression.Reference reference) ||
            !reference.root().equals("object") ||
            !(binary.right() instanceof QueryExpression.Literal literal) ||
            !(literal.value() instanceof String)
        ) {
            throw invalid("contains requires an object field and a string literal");
        }
        QueryFieldDescriptor field = resolve(reference, resolver);
        requireOperator(field, QueryOperator.CONTAINS);
        if (!isStringLike(field.type())) {
            throw invalid("contains is supported only for string-like query fields");
        }
    }

    private static QueryFieldDescriptor resolve(QueryExpression.Reference reference, FieldResolver resolver) {
        if (!reference.root().equals("object")) {
            throw invalid("filterBy may reference only object fields");
        }
        return resolver.field(new QueryPath(reference.path())).orElseThrow(() -> invalid("unknown query path"));
    }

    private static void requireOperator(QueryFieldDescriptor field, QueryOperator operator) {
        if (!field.supports(operator)) {
            throw invalid("operator is not supported for query path " + field.path().text());
        }
    }

    private static boolean isOrdering(QueryOperator operator) {
        return (
            operator == QueryOperator.GT ||
            operator == QueryOperator.GTE ||
            operator == QueryOperator.LT ||
            operator == QueryOperator.LTE
        );
    }

    private static boolean isOrderable(TypeDescriptor type) {
        Class<?> raw = type.rawType();
        if (raw == Boolean.class || raw == boolean.class || raw == Void.class || raw == void.class) {
            return false;
        }
        return raw.isPrimitive() || Comparable.class.isAssignableFrom(raw);
    }

    private static boolean isStringLike(TypeDescriptor type) {
        return CharSequence.class.isAssignableFrom(type.rawType());
    }

    private static QueryOperator operator(QueryExpression.BinaryOperator operator) {
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
            case IN, ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> throw invalid("unsupported filterBy operator");
        };
    }

    private static FilterByException invalid(String message) {
        return new FilterByException(message);
    }

    @FunctionalInterface
    private interface FieldResolver {
        Optional<QueryFieldDescriptor> field(QueryPath path);
    }
}
