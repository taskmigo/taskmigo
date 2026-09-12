package io.taskmigo.language;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/// Evaluates typed Embedded Language Semantic AST against an immutable approved environment.
@SuppressWarnings("checkstyle:NeedBraces")
public final class EmbeddedLanguageEvaluator {

    /// Evaluates a program and returns a value conforming to its static result contract.
    public @Nullable Object evaluate(SemanticAst program, Map<String, ?> roots) {
        EvaluationFrame frame = EvaluationFrame.of(roots);
        try {
            Object result = value(program.expression(), frame);
            if (!conforms(result, program.resultType(), program.resultNullable())) {
                throw failure("program result has an incompatible runtime type", program.expression().span());
            }
            return result;
        } catch (EmbeddedLanguageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure(
                exception.getMessage() == null ? "program evaluation failed" : exception.getMessage(),
                program.expression().span()
            );
        }
    }

    /// Evaluates a program after checking that its schema identity is unchanged.
    public @Nullable Object evaluate(SemanticAst program, EnvironmentSchema schema, Map<String, ?> roots) {
        if (!program.schemaFingerprint().isEmpty() && !program.schemaFingerprint().equals(schema.fingerprint())) {
            throw failure("compiled program schema does not match the evaluation schema", program.expression().span());
        }
        return this.evaluate(program, roots);
    }

    static Object compute(SemanticAst.UnaryOperator operator, @Nullable Object operand) {
        return switch (operator) {
            case NOT -> !requireBoolean(operand);
            case PLUS -> number(operand);
            case MINUS -> number(operand).negate();
        };
    }

    static Object compute(SemanticAst.BinaryOperator operator, @Nullable Object left, @Nullable Object right) {
        return switch (operator) {
            case EQUAL -> equal(left, right);
            case NOT_EQUAL -> !equal(left, right);
            case GREATER -> compare(left, right) > 0;
            case GREATER_OR_EQUAL -> compare(left, right) >= 0;
            case LESS -> compare(left, right) < 0;
            case LESS_OR_EQUAL -> compare(left, right) <= 0;
            case IN -> contains(list(right), left);
            case ADD -> number(left).add(number(right));
            case SUBTRACT -> number(left).subtract(number(right));
            case MULTIPLY -> number(left).multiply(number(right));
            case DIVIDE -> divide(number(left), number(right));
            case MODULO -> modulo(number(left), number(right));
            case AND, OR -> throw new IllegalArgumentException("logical short-circuit operation requires evaluator");
        };
    }

    private static boolean equal(@Nullable Object left, @Nullable Object right) {
        if (left instanceof Number && right instanceof Number) {
            return number(left).compareTo(number(right)) == 0;
        }
        if (left instanceof List<?> leftValues && right instanceof List<?> rightValues) {
            if (leftValues.size() != rightValues.size()) return false;
            for (int index = 0; index < leftValues.size(); index++) {
                if (!equal(leftValues.get(index), rightValues.get(index))) return false;
            }
            return true;
        }
        return left == null ? right == null : left.equals(right);
    }

    private static boolean contains(List<?> values, @Nullable Object expected) {
        for (Object value : values) {
            if (equal(expected, value)) return true;
        }
        return false;
    }

    private static @Nullable Object value(SemanticAst.Expression expression, EvaluationFrame frame) {
        return switch (expression) {
            case SemanticAst.Literal literal -> literal.value();
            case SemanticAst.Reference reference -> frame.read(reference);
            case SemanticAst.ListLiteral list -> list(list, frame);
            case SemanticAst.Unary unary -> compute(unary.operator(), value(unary.operand(), frame));
            case SemanticAst.Binary binary -> binary(binary, frame);
            case SemanticAst.Conditional conditional -> requireBoolean(value(conditional.condition(), frame))
                ? value(conditional.whenTrue(), frame)
                : value(conditional.whenFalse(), frame);
            case SemanticAst.Quantifier quantifier -> quantifier(quantifier, frame);
            case SemanticAst.Length length -> length(length, frame);
        };
    }

    private static List<@Nullable Object> list(SemanticAst.ListLiteral expression, EvaluationFrame frame) {
        List<@Nullable Object> result = new ArrayList<>(expression.values().size());
        for (SemanticAst.Expression value : expression.values()) {
            result.add(value(value, frame));
        }
        return Collections.unmodifiableList(result);
    }

    private static boolean quantifier(SemanticAst.Quantifier expression, EvaluationFrame frame) {
        List<?> values = list(value(expression.collection(), frame));
        for (Object element : values) {
            frame.push(expression.elementName(), element);
            boolean matches;
            try {
                matches = requireBoolean(value(expression.predicate(), frame));
            } finally {
                frame.pop();
            }
            if (expression.operator() == SemanticAst.QuantifierOperator.ALL && !matches) return false;
            if (expression.operator() == SemanticAst.QuantifierOperator.ANY && matches) return true;
            if (expression.operator() == SemanticAst.QuantifierOperator.NONE && matches) return false;
        }
        return expression.operator() != SemanticAst.QuantifierOperator.ANY;
    }

    private static BigDecimal length(SemanticAst.Length expression, EvaluationFrame frame) {
        Object value = value(expression.operand(), frame);
        if (value instanceof String text) return BigDecimal.valueOf(text.length());
        if (value instanceof List<?> list) return BigDecimal.valueOf(list.size());
        throw failure("len requires a String or List", expression.span());
    }

    private static Object binary(SemanticAst.Binary binary, EvaluationFrame frame) {
        Object left = value(binary.left(), frame);
        if (binary.operator() == SemanticAst.BinaryOperator.AND && left instanceof Boolean bool) {
            if (!bool) return false;
            return requireBoolean(value(binary.right(), frame));
        }
        if (binary.operator() == SemanticAst.BinaryOperator.OR && left instanceof Boolean bool) {
            if (bool) return true;
            return requireBoolean(value(binary.right(), frame));
        }
        return compute(binary.operator(), left, value(binary.right(), frame));
    }

    static @Nullable Object read(SemanticAst.Reference reference, Map<String, ?> roots) {
        return EvaluationFrame.of(roots).read(reference);
    }

    static boolean matchesType(@Nullable Object value, LanguageType type) {
        return switch (type) {
            case LanguageType.Scalar scalar -> switch (scalar) {
                case BOOL -> value instanceof Boolean;
                case STRING -> value instanceof String;
                case NUMBER -> value instanceof Number number && finite(number);
                case NULL -> value == null;
            };
            case LanguageType.ListType list -> value instanceof List<?> values && allMatch(values, list.elementType());
            case LanguageType.StructuredType structured -> value instanceof Map<?, ?> map && matchesFields(map, structured);
        };
    }

    private static boolean allMatch(List<?> values, LanguageType type) {
        for (Object value : values) {
            if (!matchesType(value, type)) return false;
        }
        return true;
    }

    private static boolean matchesFields(Map<?, ?> values, LanguageType.StructuredType type) {
        for (Map.Entry<String, EnvironmentSchema.Field> entry : type.fields().entrySet()) {
            Object nested = values.get(entry.getKey());
            EnvironmentSchema.Field field = entry.getValue();
            if (nested != null) {
                if (!matchesType(nested, field.type())) return false;
            } else if (!field.nullable() && field.type() != LanguageType.Scalar.NULL) {
                return false;
            }
        }
        return true;
    }

    private static boolean conforms(@Nullable Object value, LanguageType type, boolean nullable) {
        return value == null ? nullable || type == LanguageType.Scalar.NULL : matchesType(value, type);
    }

    private static boolean finite(Number number) {
        if (number instanceof Double value) return Double.isFinite(value);
        if (number instanceof Float value) return Float.isFinite(value);
        try {
            number(number);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Boolean requireBoolean(@Nullable Object value) {
        if (!(value instanceof Boolean result)) throw new IllegalArgumentException(
            "Embedded Language value is not Bool"
        );
        return result;
    }

    private static BigDecimal number(@Nullable Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException(
            "Embedded Language value is not Number"
        );
        if (number instanceof BigDecimal decimal) return decimal;
        if (number instanceof BigInteger integer) return new BigDecimal(integer);
        if (
            number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long
        ) return BigDecimal.valueOf(number.longValue());
        if (number instanceof Double doubleValue) {
            if (!Double.isFinite(doubleValue)) throw new IllegalArgumentException(
                "Embedded Language Number must be finite"
            );
            return BigDecimal.valueOf(doubleValue);
        }
        if (number instanceof Float floatValue) {
            if (!Float.isFinite(floatValue)) throw new IllegalArgumentException(
                "Embedded Language Number must be finite"
            );
            return BigDecimal.valueOf(floatValue.doubleValue());
        }
        try {
            return new BigDecimal(number.toString());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Embedded Language Number must be finite", exception);
        }
    }

    private static List<?> list(@Nullable Object value) {
        if (!(value instanceof List<?> result)) throw new IllegalArgumentException(
            "Embedded Language value is not a List"
        );
        return result;
    }

    private static int compare(@Nullable Object left, @Nullable Object right) {
        if (left instanceof String leftText && right instanceof String rightText) return leftText.compareTo(rightText);
        return number(left).compareTo(number(right));
    }

    private static BigDecimal divide(BigDecimal left, BigDecimal right) {
        if (right.signum() == 0) throw new IllegalArgumentException("division by zero");
        return left.divide(right, MathContext.DECIMAL128);
    }

    private static BigDecimal modulo(BigDecimal left, BigDecimal right) {
        if (right.signum() == 0) throw new IllegalArgumentException("modulo by zero");
        return left.remainder(right);
    }

    private static EmbeddedLanguageException failure(String message, LanguageDiagnostic.SourceSpan span) {
        return new EmbeddedLanguageException(
            new LanguageDiagnostic(LanguageDiagnostic.Category.TypeError, message, span)
        );
    }
}
