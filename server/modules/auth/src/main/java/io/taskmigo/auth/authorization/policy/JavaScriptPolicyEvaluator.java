package io.taskmigo.auth.authorization.policy;

import io.taskmigo.auth.authorization.AuthorizationException;
import java.math.BigDecimal;
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
        if (!(value instanceof Boolean result)) {
            throw new AuthorizationException("Authorization policy must return a boolean");
        }
        return result;
    }

    @Nullable
    Object evaluateValue(PolicyIr.Expression expression, Map<String, ?> roots) {
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
            case PolicyIr.Binary binary -> this.binary(binary, roots);
            case PolicyIr.Unary unary -> this.unary(unary, roots);
            case PolicyIr.Conditional conditional -> this.truthy(this.value(conditional.condition(), roots))
                ? this.value(conditional.whenTrue(), roots)
                : this.value(conditional.whenFalse(), roots);
        };
    }

    private @Nullable Object reference(PolicyIr.Reference reference, Map<String, ?> roots) {
        Object current = roots.containsKey(reference.root()) ? roots.get(reference.root()) : UNDEFINED;
        for (String part : reference.path()) {
            current = propertyValue(current, part);
        }
        return current;
    }

    private @Nullable Object property(PolicyIr.PropertyAccess property, Map<String, ?> roots) {
        return propertyValue(this.value(property.target(), roots), property.property());
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
            case ADD, SUBTRACT, MULTIPLY, DIVIDE -> arithmetic(binary.operator(), left, right);
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

    private static @Nullable Object propertyValue(@Nullable Object target, String property) {
        if (target instanceof Map<?, ?> map) {
            return map.containsKey(property) ? map.get(property) : UNDEFINED;
        }
        return UNDEFINED;
    }

    private static boolean strictEquals(@Nullable Object left, @Nullable Object right) {
        return switch (left) {
            case null -> right == null;
            case Number leftNumber when right instanceof Number rightNumber -> {
                double leftValue = leftNumber.doubleValue();
                double rightValue = rightNumber.doubleValue();
                yield !Double.isNaN(leftValue) && !Double.isNaN(rightValue) && leftValue == rightValue;
            }
            case String leftString when right instanceof String rightString -> leftString.equals(rightString);
            case Boolean leftBoolean when right instanceof Boolean rightBoolean -> leftBoolean.equals(rightBoolean);
            default -> left == right;
        };
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

    private static double arithmetic(PolicyIr.BinaryOperator operator, @Nullable Object left, @Nullable Object right) {
        double leftValue = number(left).doubleValue();
        double rightValue = number(right).doubleValue();
        return switch (operator) {
            case ADD -> leftValue + rightValue;
            case SUBTRACT -> leftValue - rightValue;
            case MULTIPLY -> leftValue * rightValue;
            case DIVIDE -> leftValue / rightValue;
            default -> throw new AssertionError("not an arithmetic operator");
        };
    }

    private static BigDecimal number(@Nullable Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        throw invalidValue("arithmetic requires numbers");
    }

    private boolean truthy(@Nullable Object value) {
        if (value == null || value == UNDEFINED || Boolean.FALSE.equals(value)) {
            return false;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0 && !Double.isNaN(number.doubleValue());
        }
        return !(value instanceof String string) || !string.isEmpty();
    }

    private static AuthorizationException invalidValue(String message) {
        return new AuthorizationException("Authorization policy evaluation failed: " + message);
    }
}
