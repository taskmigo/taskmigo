package io.taskmigo.auth.authorization.policy;

import io.taskmigo.auth.authorization.AuthorizationException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/// Evaluates compiled policy IR without exposing a JavaScript runtime to policy authors.
@Service
public final class JavaScriptPolicyEvaluator {

    private static final Object UNDEFINED = new Object();

    /// Evaluates a compiled policy against the approved authorization roots.
    ///
    /// @param policy the parser-independent policy representation
    /// @param roots the immutable request and principal values exposed to the policy
    /// @return the policy's boolean result
    /// @throws AuthorizationException when evaluation fails or the result is not boolean
    public boolean evaluate(PolicyIr policy, Map<String, ?> roots) {
        Object value = this.value(policy.expression(), roots);
        if (!(value instanceof Boolean result)) throw new AuthorizationException(
            "Authorization policy must return a boolean"
        );
        return result;
    }

    @Nullable Object evaluateValue(PolicyIr.Expression expression, Map<String, ?> roots) {
        return this.value(expression, roots);
    }

    static Object undefinedValue() {
        return UNDEFINED;
    }

    private @Nullable Object value(PolicyIr.Expression expression, Map<String, ?> roots) {
        return switch (expression) {
            case PolicyIr.Literal literal -> literal.value();
            case PolicyIr.UndefinedValue ignored -> UNDEFINED;
            case PolicyIr.Reference reference -> this.reference(reference, roots);
            case PolicyIr.PropertyAccess property -> this.property(property, roots);
            case PolicyIr.ArrayValue array -> array.values().stream().map(item -> this.value(item, roots)).toList();
            case PolicyIr.ObjectValue object -> this.object(object, roots);
            case PolicyIr.Binary binary -> this.binary(binary, roots);
            case PolicyIr.Unary unary -> this.unary(unary, roots);
            case PolicyIr.Conditional conditional -> this.truthy(this.value(conditional.condition(), roots))
                ? this.value(conditional.whenTrue(), roots)
                : this.value(conditional.whenFalse(), roots);
        };
    }

    private @Nullable Object reference(PolicyIr.Reference reference, Map<String, ?> roots) {
        Object current = roots.containsKey(reference.root()) ? roots.get(reference.root()) : UNDEFINED;
        for (String part : reference.path()) current = propertyValue(current, part);
        return current;
    }

    private @Nullable Object property(PolicyIr.PropertyAccess property, Map<String, ?> roots) {
        return propertyValue(this.value(property.target(), roots), property.property());
    }

    private Map<String, @Nullable Object> object(PolicyIr.ObjectValue object, Map<String, ?> roots) {
        Map<String, @Nullable Object> values = new LinkedHashMap<>();
        object.values().forEach((key, value) -> values.put(key, this.value(value, roots)));
        return Collections.unmodifiableMap(values);
    }

    private @Nullable Object binary(PolicyIr.Binary binary, Map<String, ?> roots) {
        if (binary.operator() == PolicyIr.BinaryOperator.AND) {
            Object left = this.value(binary.left(), roots);
            return this.truthy(left) ? this.value(binary.right(), roots) : left;
        }
        if (binary.operator() == PolicyIr.BinaryOperator.OR) {
            Object left = this.value(binary.left(), roots);
            return this.truthy(left) ? left : this.value(binary.right(), roots);
        }
        Object left = this.value(binary.left(), roots);
        Object right = this.value(binary.right(), roots);
        return switch (binary.operator()) {
            case EQUAL -> strictEquals(left, right);
            case NOT_EQUAL -> !strictEquals(left, right);
            case GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL -> compare(binary.operator(), left, right);
            case ADD -> add(left, right);
            case SUBTRACT -> arithmetic(binary.operator(), left, right);
            case MULTIPLY -> arithmetic(binary.operator(), left, right);
            case DIVIDE -> arithmetic(binary.operator(), left, right);
            case MODULO -> arithmetic(binary.operator(), left, right);
            case IN -> containsProperty(right, left);
            case CONTAINS -> contains(left, right);
            case STARTS_WITH -> stringPredicate(binary.operator(), left, right);
            case ENDS_WITH -> stringPredicate(binary.operator(), left, right);
            case AND, OR -> throw new AssertionError("logical operators are handled before operands");
        };
    }

    private Object unary(PolicyIr.Unary unary, Map<String, ?> roots) {
        Object operand = this.value(unary.operand(), roots);
        return switch (unary.operator()) {
            case NOT -> !this.truthy(operand);
            case PLUS -> number(operand);
            case MINUS -> -number(operand).doubleValue();
        };
    }

    private static Object propertyValue(@Nullable Object target, String property) {
        if (target instanceof Map<?, ?> map) return map.containsKey(property) ? map.get(property) : UNDEFINED;
        if (target instanceof List<?> list && property.equals("length")) return (double) list.size();
        if (target instanceof List<?> list && property.matches("0|[1-9][0-9]*")) {
            int index = Integer.parseInt(property);
            return index < list.size() ? list.get(index) : UNDEFINED;
        }
        if (target instanceof String string && property.equals("length")) return (double) string.length();
        if (target instanceof String string && property.matches("0|[1-9][0-9]*")) {
            int index = Integer.parseInt(property);
            return index < string.length() ? String.valueOf(string.charAt(index)) : UNDEFINED;
        }
        return UNDEFINED;
    }

    private static boolean strictEquals(@Nullable Object left, @Nullable Object right) {
        if (left == UNDEFINED || right == UNDEFINED) return left == right;
        if (left == null || right == null) return left == right;
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            double leftValue = leftNumber.doubleValue();
            double rightValue = rightNumber.doubleValue();
            return !Double.isNaN(leftValue) && !Double.isNaN(rightValue) && leftValue == rightValue;
        }
        if (left instanceof String && right instanceof String) return left.equals(right);
        if (left instanceof Boolean && right instanceof Boolean) return left.equals(right);
        return left == right;
    }

    private static boolean compare(PolicyIr.BinaryOperator operator, @Nullable Object left, @Nullable Object right) {
        if (left instanceof String leftString && right instanceof String rightString) {
            int comparison = leftString.compareTo(rightString);
            return comparison(operator, comparison);
        }
        double leftValue = number(left).doubleValue();
        double rightValue = number(right).doubleValue();
        return switch (operator) {
            case GREATER -> leftValue > rightValue;
            case GREATER_OR_EQUAL -> leftValue >= rightValue;
            case LESS -> leftValue < rightValue;
            case LESS_OR_EQUAL -> leftValue <= rightValue;
            default -> throw new AssertionError("not a comparison operator");
        };
    }

    private static boolean comparison(PolicyIr.BinaryOperator operator, int comparison) {
        return switch (operator) {
            case GREATER -> comparison > 0;
            case GREATER_OR_EQUAL -> comparison >= 0;
            case LESS -> comparison < 0;
            case LESS_OR_EQUAL -> comparison <= 0;
            default -> throw new AssertionError("not a comparison operator");
        };
    }

    private static Object add(@Nullable Object left, @Nullable Object right) {
        if (left instanceof String || right instanceof String) return stringify(left) + stringify(right);
        return number(left).doubleValue() + number(right).doubleValue();
    }

    private static double arithmetic(PolicyIr.BinaryOperator operator, @Nullable Object left, @Nullable Object right) {
        double leftValue = number(left).doubleValue();
        double rightValue = number(right).doubleValue();
        return switch (operator) {
            case SUBTRACT -> leftValue - rightValue;
            case MULTIPLY -> leftValue * rightValue;
            case DIVIDE -> leftValue / rightValue;
            case MODULO -> leftValue % rightValue;
            default -> throw new AssertionError("not an arithmetic operator");
        };
    }

    private static boolean containsProperty(@Nullable Object container, @Nullable Object property) {
        if (container instanceof Map<?, ?> map) return map.containsKey(property);
        if (container instanceof List<?> list && property instanceof Number number) {
            int index = number.intValue();
            return index >= 0 && index < list.size();
        }
        throw invalidValue("in requires an object or array");
    }

    private static boolean contains(@Nullable Object value, @Nullable Object searched) {
        if (value instanceof List<?> list) return list.stream().anyMatch(item -> strictEquals(item, searched));
        if (value instanceof String string && searched instanceof String text) return string.contains(text);
        throw invalidValue("includes requires an array or string");
    }

    private static boolean stringPredicate(
        PolicyIr.BinaryOperator operator,
        @Nullable Object left,
        @Nullable Object right
    ) {
        if (!(left instanceof String text) || !(right instanceof String searched)) throw invalidValue(
            operator.name().toLowerCase() + " requires strings"
        );
        return operator == PolicyIr.BinaryOperator.STARTS_WITH
            ? text.startsWith(searched)
            : text.endsWith(searched);
    }

    private static BigDecimal number(@Nullable Object value) {
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        throw invalidValue("arithmetic requires numbers");
    }

    private static String stringify(@Nullable Object value) {
        if (value == null) return "null";
        if (value == UNDEFINED) return "undefined";
        return String.valueOf(value);
    }

    private boolean truthy(@Nullable Object value) {
        if (value == null || value == UNDEFINED || Boolean.FALSE.equals(value)) return false;
        if (value instanceof Number number) return number.doubleValue() != 0 && !Double.isNaN(number.doubleValue());
        return !(value instanceof String string) || !string.isEmpty();
    }

    private static AuthorizationException invalidValue(String message) {
        return new AuthorizationException("Authorization policy evaluation failed: " + message);
    }
}
