package io.taskmigo.policy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/// Evaluates typed Policy Language IR against an immutable approved environment.
@SuppressWarnings("checkstyle:NeedBraces")
public final class PolicyEvaluator {

    /// Evaluates a policy and returns its required boolean result.
    public boolean evaluate(PolicyIr policy, Map<String, ?> roots) {
        try {
            Object value = value(policy.expression(), roots);
            if (!(value instanceof Boolean result)) {
                throw failure("policy result is not Bool", policy.expression().span());
            }
            return result;
        } catch (PolicyException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(
                exception.getMessage() == null ? "policy evaluation failed" : exception.getMessage(),
                policy.expression().span()
            );
        }
    }

    /// Evaluates a policy after checking that its schema identity is unchanged.
    public boolean evaluate(PolicyIr policy, EnvironmentSchema schema, Map<String, ?> roots) {
        if (!policy.schemaFingerprint().isEmpty() && !policy.schemaFingerprint().equals(schema.fingerprint())) {
            throw failure("compiled policy schema does not match the evaluation schema", policy.expression().span());
        }
        return evaluate(policy, roots);
    }

    static Object compute(PolicyIr.UnaryOperator operator, @Nullable Object operand) {
        return switch (operator) {
            case NOT -> !requireBoolean(operand);
            case PLUS -> number(operand);
            case MINUS -> number(operand).negate();
        };
    }

    static Object compute(PolicyIr.BinaryOperator operator, @Nullable Object left, @Nullable Object right) {
        return switch (operator) {
            case EQUAL -> left == null ? right == null : left.equals(right);
            case NOT_EQUAL -> left == null ? right != null : !left.equals(right);
            case GREATER -> compare(left, right) > 0;
            case GREATER_OR_EQUAL -> compare(left, right) >= 0;
            case LESS -> compare(left, right) < 0;
            case LESS_OR_EQUAL -> compare(left, right) <= 0;
            case IN -> list(right).contains(left);
            case ADD -> number(left).add(number(right));
            case SUBTRACT -> number(left).subtract(number(right));
            case MULTIPLY -> number(left).multiply(number(right));
            case DIVIDE -> divide(number(left), number(right));
            case MODULO -> modulo(number(left), number(right));
            case AND, OR -> throw new IllegalArgumentException("logical short-circuit operation requires evaluator");
        };
    }

    private static @Nullable Object value(PolicyIr.Expression expression, Map<String, ?> roots) {
        return switch (expression) {
            case PolicyIr.Literal literal -> literal.value();
            case PolicyIr.Reference reference -> reference(reference, roots);
            case PolicyIr.ListLiteral list -> list.values()
                .stream()
                .map(value -> value(value, roots))
                .toList();
            case PolicyIr.Unary unary -> compute(unary.operator(), value(unary.operand(), roots));
            case PolicyIr.Binary binary -> binary(binary, roots);
            case PolicyIr.Conditional conditional -> requireBoolean(value(conditional.condition(), roots))
                ? value(conditional.whenTrue(), roots)
                : value(conditional.whenFalse(), roots);
        };
    }

    private static @Nullable Object binary(PolicyIr.Binary binary, Map<String, ?> roots) {
        Object left = value(binary.left(), roots);
        if (binary.operator() == PolicyIr.BinaryOperator.AND && left instanceof Boolean bool) {
            if (!bool) return false;
            return requireBoolean(value(binary.right(), roots));
        }
        if (binary.operator() == PolicyIr.BinaryOperator.OR && left instanceof Boolean bool) {
            if (bool) return true;
            return requireBoolean(value(binary.right(), roots));
        }
        return compute(binary.operator(), left, value(binary.right(), roots));
    }

    private static @Nullable Object reference(PolicyIr.Reference reference, Map<String, ?> roots) {
        if (!roots.containsKey(reference.root())) throw failure(
            "missing policy root: " + reference.root(),
            reference.span()
        );
        Object current = roots.get(reference.root());
        for (String name : reference.path()) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(name)) {
                throw failure(
                    "missing policy value: " + reference.root() + "." + String.join(".", reference.path()),
                    reference.span()
                );
            }
            current = map.get(name);
        }
        if (current == null) {
            if (reference.nullable()) return null;
            throw failure("non-nullable policy value is null", reference.span());
        }
        if (!matchesType(current, reference.type())) throw failure(
            "policy input has an incompatible type",
            reference.span()
        );
        return current;
    }

    private static boolean matchesType(Object value, PolicyType type) {
        return switch (type) {
            case PolicyType.Scalar scalar -> switch (scalar) {
                case BOOL -> value instanceof Boolean;
                case STRING -> value instanceof String;
                case NUMBER -> value instanceof Number number && finite(number);
                case NULL -> value == null;
            };
            case PolicyType.ListType list -> value instanceof List<?> values &&
                values.stream().allMatch(item -> item != null && matchesType(item, list.elementType()));
        };
    }

    private static boolean finite(Number number) {
        try {
            new BigDecimal(number.toString());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static Boolean requireBoolean(@Nullable Object value) {
        if (!(value instanceof Boolean result)) throw new IllegalArgumentException("Policy Language value is not Bool");
        return result;
    }

    private static BigDecimal number(@Nullable Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException(
            "Policy Language value is not Number"
        );
        try {
            return new BigDecimal(number.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Policy Language Number must be finite", exception);
        }
    }

    private static List<?> list(@Nullable Object value) {
        if (!(value instanceof List<?> result)) throw new IllegalArgumentException(
            "Policy Language value is not a List"
        );
        return result;
    }

    private static int compare(@Nullable Object left, @Nullable Object right) {
        if (left instanceof String leftText && right instanceof String rightText) return leftText.compareTo(rightText);
        return number(left).compareTo(number(right));
    }

    private static BigDecimal divide(BigDecimal left, BigDecimal right) {
        if (right.signum() == 0) throw new IllegalArgumentException("division by zero");
        return left.divide(right, java.math.MathContext.DECIMAL128);
    }

    private static BigDecimal modulo(BigDecimal left, BigDecimal right) {
        if (right.signum() == 0) throw new IllegalArgumentException("modulo by zero");
        return left.remainder(right);
    }

    private static PolicyException failure(String message, PolicyDiagnostic.SourceSpan span) {
        return new PolicyException(new PolicyDiagnostic(PolicyDiagnostic.Category.TypeError, message, span));
    }
}
