package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationExpression.Binary;
import io.taskmigo.authorization.AuthorizationExpression.BinaryOperator;
import io.taskmigo.authorization.AuthorizationExpression.Literal;
import io.taskmigo.authorization.AuthorizationExpression.Reference;
import io.taskmigo.authorization.AuthorizationExpression.Unary;
import io.taskmigo.authorization.AuthorizationExpression.UnaryOperator;
import io.taskmigo.authorization.AuthorizationExpression.ValueType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

final class AuthorizationEvaluator {

    boolean test(AuthorizationExpression expression, Map<String, Object> values) {
        Object result = value(expression, values);
        if (!(result instanceof Boolean bool)) throw new IllegalArgumentException("Authorization condition did not evaluate to boolean");
        return bool;
    }

    AuthorizationExpression specialize(AuthorizationExpression expression, Map<String, Object> values) {
        return switch (expression) {
            case Reference reference when reference.root() != AuthorizationExpression.Root.OBJECT -> literal(resolve(reference, values));
            case Literal literal -> literal;
            case Reference reference -> reference;
            case Unary(var operator, var operand) -> new Unary(operator, specialize(operand, values));
            case Binary(var operator, var left, var right) -> new Binary(operator, specialize(left, values), specialize(right, values));
        };
    }

    private Object value(AuthorizationExpression expression, Map<String, Object> values) {
        return switch (expression) {
            case Literal(var value, var ignored) -> value;
            case Reference reference -> resolve(reference, values);
            case Unary(var operator, var operand) -> unary(operator, value(operand, values));
            case Binary(var operator, var left, var right) -> binary(operator, value(left, values), value(right, values));
        };
    }

    private static Object unary(UnaryOperator operator, @Nullable Object operand) {
        return switch (operator) {
            case NOT -> !requireBoolean(operand);
            case NEGATE -> requireNumber(operand).negate();
            case POSITIVE -> requireNumber(operand);
        };
    }

    private static Object binary(BinaryOperator operator, @Nullable Object left, @Nullable Object right) {
        return switch (operator) {
            case EQ -> Objects.equals(normalize(left), normalize(right));
            case NE -> !Objects.equals(normalize(left), normalize(right));
            case GT -> compare(left, right) > 0;
            case GE -> compare(left, right) >= 0;
            case LT -> compare(left, right) < 0;
            case LE -> compare(left, right) <= 0;
            case AND -> requireBoolean(left) && requireBoolean(right);
            case OR -> requireBoolean(left) || requireBoolean(right);
            case ADD -> requireNumber(left).add(requireNumber(right));
            case SUBTRACT -> requireNumber(left).subtract(requireNumber(right));
            case MULTIPLY -> requireNumber(left).multiply(requireNumber(right));
            case DIVIDE -> requireNumber(left).divide(requireNumber(right), MathContext.DECIMAL128);
            case MODULUS -> requireNumber(left).remainder(requireNumber(right));
        };
    }

    private static int compare(@Nullable Object left, @Nullable Object right) {
        if (left == null || right == null) throw new IllegalArgumentException("Ordered authorization comparison cannot use null");
        if (left instanceof Number && right instanceof Number) return requireNumber(left).compareTo(requireNumber(right));
        if (left instanceof String leftString && right instanceof String rightString) return leftString.compareTo(rightString);
        throw new IllegalArgumentException("Incompatible ordered authorization operands");
    }

    private static boolean requireBoolean(@Nullable Object value) {
        if (!(value instanceof Boolean bool)) throw new IllegalArgumentException("Authorization boolean operand is unavailable or invalid");
        return bool;
    }

    private static BigDecimal requireNumber(@Nullable Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("Authorization numeric operand is unavailable or invalid");
        return new BigDecimal(number.toString());
    }

    private static @Nullable Object normalize(@Nullable Object value) {
        return value instanceof Number ? requireNumber(value) : value;
    }

    private static Literal literal(@Nullable Object value) {
        ValueType type = value == null
            ? ValueType.NULL
            : value instanceof Boolean
                ? ValueType.BOOLEAN
                : value instanceof Number
                    ? ValueType.NUMBER
                    : value instanceof String
                        ? ValueType.STRING
                        : ValueType.UNKNOWN;
        return new Literal(value, type);
    }

    private static @Nullable Object resolve(Reference reference, Map<String, Object> values) {
        String key = reference.canonicalPath();
        if (!values.containsKey(key)) throw new IllegalArgumentException("Missing authorization context value: " + key);
        Object value = values.get(key);
        if (value != null && !(value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof java.util.UUID)) {
            throw new IllegalArgumentException("Unsafe authorization context value type for " + key);
        }
        return value;
    }
}
