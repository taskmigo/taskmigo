package io.taskmigo.auth;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/// Evaluates the immutable expression tree produced by {@link AuthorizationCompiler} against request values.
@Service
public final class AuthorizationExpressionEvaluator {

    /// Evaluates an authorization expression using the supplied principal, request, and object roots.
    ///
    /// @param expression the compiled authorization expression
    /// @param roots the values exposed to the expression roots
    /// @return the boolean result of the expression
    /// @throws AuthorizationException when the expression produces an unsupported value
    public boolean evaluate(AuthorizationCompiler.Expression expression, Map<String, ?> roots) {
        Object value = this.value(expression, roots);
        if (!(value instanceof Boolean result)) throw new AuthorizationException(
            "Authorization condition is not boolean"
        );
        return result;
    }

    private @Nullable Object value(AuthorizationCompiler.Expression expression, Map<String, ?> roots) {
        return switch (expression) {
            case AuthorizationCompiler.LiteralValue literal -> literal.value();
            case AuthorizationCompiler.Reference reference -> this.reference(reference, roots);
            case AuthorizationCompiler.Binary binary -> this.binary(binary, roots);
            case AuthorizationCompiler.Unary unary -> this.unary(unary, roots);
        };
    }

    private @Nullable Object reference(AuthorizationCompiler.Reference reference, Map<String, ?> roots) {
        Object current = roots.get(reference.root());
        for (String part : reference.path()) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
        }
        return current;
    }

    private Object binary(AuthorizationCompiler.Binary binary, Map<String, ?> roots) {
        if (binary.operator() == AuthorizationCompiler.BinaryOperator.AND) {
            return this.booleanValue(binary.left(), roots) && this.booleanValue(binary.right(), roots);
        }
        if (binary.operator() == AuthorizationCompiler.BinaryOperator.OR) {
            return this.booleanValue(binary.left(), roots) || this.booleanValue(binary.right(), roots);
        }

        Object left = this.value(binary.left(), roots);
        Object right = this.value(binary.right(), roots);
        return switch (binary.operator()) {
            case EQUAL -> equal(left, right);
            case NOT_EQUAL -> left != null && right != null && !equal(left, right);
            case GREATER -> compare(left, right) > 0;
            case GREATER_OR_EQUAL -> compare(left, right) >= 0;
            case LESS -> compare(left, right) < 0;
            case LESS_OR_EQUAL -> compare(left, right) <= 0;
            case ADD -> arithmetic(left, right, '+');
            case SUBTRACT -> arithmetic(left, right, '-');
            case MULTIPLY -> arithmetic(left, right, '*');
            case DIVIDE -> arithmetic(left, right, '/');
            case MODULO -> arithmetic(left, right, '%');
            case OR, AND -> throw new IllegalStateException("Boolean operator was evaluated eagerly");
        };
    }

    private Object unary(AuthorizationCompiler.Unary unary, Map<String, ?> roots) {
        Object operand = this.value(unary.operand(), roots);
        return switch (unary.operator()) {
            case NOT -> !this.booleanValue(unary.operand(), roots);
            case PLUS -> number(operand);
            case MINUS -> number(operand).negate();
        };
    }

    private boolean booleanValue(AuthorizationCompiler.Expression expression, Map<String, ?> roots) {
        Object value = this.value(expression, roots);
        if (!(value instanceof Boolean result)) throw new AuthorizationException(
            "Authorization condition is not boolean"
        );
        return result;
    }

    private static boolean equal(@Nullable Object left, @Nullable Object right) {
        if (left instanceof Number && right instanceof Number) return number(left).compareTo(number(right)) == 0;
        return Objects.equals(left, right);
    }

    private static int compare(@Nullable Object left, @Nullable Object right) {
        if (left instanceof Number && right instanceof Number) return number(left).compareTo(number(right));
        if (left instanceof String leftString && right instanceof String rightString) return leftString.compareTo(
            rightString
        );
        throw new AuthorizationException("Authorization condition values are not comparable");
    }

    private static BigDecimal arithmetic(@Nullable Object left, @Nullable Object right, char operator) {
        BigDecimal leftNumber = number(left);
        BigDecimal rightNumber = number(right);
        return switch (operator) {
            case '+' -> leftNumber.add(rightNumber);
            case '-' -> leftNumber.subtract(rightNumber);
            case '*' -> leftNumber.multiply(rightNumber);
            case '/' -> leftNumber.divide(rightNumber, MathContext.DECIMAL128);
            case '%' -> leftNumber.remainder(rightNumber);
            default -> throw new IllegalArgumentException("Unknown arithmetic operator");
        };
    }

    private static BigDecimal number(@Nullable Object value) {
        if (!(value instanceof Number number)) throw new AuthorizationException(
            "Authorization condition value is not numeric"
        );
        return new BigDecimal(number.toString());
    }
}
