package io.taskmigo.embeddedlanguage;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/// Evaluates typed Embedded Language Semantic AST against an immutable approved environment.
@SuppressWarnings("checkstyle:NeedBraces")
public final class EmbeddedLanguageEvaluator {

    /// Evaluates a program and returns a value conforming to its static result contract.
    public @Nullable Object evaluate(SemanticAst program, Map<String, ?> roots) {
        try {
            @Nullable
            Object result = value(program.expression(), roots);
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
        return evaluate(program, roots);
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
            case IN -> list(right)
                .stream()
                .anyMatch(value -> equal(left, value));
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
            return (
                leftValues.size() == rightValues.size() &&
                java.util.stream.IntStream.range(0, leftValues.size()).allMatch(index ->
                    equal(leftValues.get(index), rightValues.get(index))
                )
            );
        }
        return left == null ? right == null : left.equals(right);
    }

    private static @Nullable Object value(SemanticAst.Expression expression, Map<String, ?> roots) {
        return switch (expression) {
            case SemanticAst.Literal literal -> literal.value();
            case SemanticAst.Reference reference -> read(reference, roots);
            case SemanticAst.ListLiteral list -> list.values()
                .stream()
                .map(value -> value(value, roots))
                .toList();
            case SemanticAst.Unary unary -> compute(unary.operator(), value(unary.operand(), roots));
            case SemanticAst.Binary binary -> binary(binary, roots);
            case SemanticAst.Conditional conditional -> requireBoolean(value(conditional.condition(), roots))
                ? value(conditional.whenTrue(), roots)
                : value(conditional.whenFalse(), roots);
            case SemanticAst.Quantifier quantifier -> quantifier(quantifier, roots);
            case SemanticAst.Length length -> length(length, roots);
        };
    }

    private static boolean quantifier(SemanticAst.Quantifier expression, Map<String, ?> roots) {
        List<?> values = list(value(expression.collection(), roots));
        for (Object element : values) {
            Map<String, Object> scoped = new HashMap<>(roots);
            Map<String, Object> binding = new HashMap<>();
            binding.put(expression.elementName(), element);
            scoped.put("__lambda__", binding);
            boolean matches = requireBoolean(value(expression.predicate(), scoped));
            if (expression.operator() == SemanticAst.QuantifierOperator.ALL && !matches) return false;
            if (expression.operator() == SemanticAst.QuantifierOperator.ANY && matches) return true;
            if (expression.operator() == SemanticAst.QuantifierOperator.NONE && matches) return false;
        }
        return expression.operator() != SemanticAst.QuantifierOperator.ANY;
    }

    private static BigDecimal length(SemanticAst.Length expression, Map<String, ?> roots) {
        Object value = value(expression.operand(), roots);
        if (value instanceof String text) return BigDecimal.valueOf(text.length());
        if (value instanceof List<?> list) return BigDecimal.valueOf(list.size());
        throw failure("len requires a String or List", expression.span());
    }

    private static @Nullable Object binary(SemanticAst.Binary binary, Map<String, ?> roots) {
        @Nullable
        Object left = value(binary.left(), roots);
        if (binary.operator() == SemanticAst.BinaryOperator.AND && left instanceof Boolean bool) {
            if (!bool) return false;
            return requireBoolean(value(binary.right(), roots));
        }
        if (binary.operator() == SemanticAst.BinaryOperator.OR && left instanceof Boolean bool) {
            if (bool) return true;
            return requireBoolean(value(binary.right(), roots));
        }
        return compute(binary.operator(), left, value(binary.right(), roots));
    }

    static @Nullable Object read(SemanticAst.Reference reference, Map<String, ?> roots) {
        if (!roots.containsKey(reference.root())) throw failure(
            "missing program root: " + reference.root(),
            reference.span()
        );
        @Nullable
        Object current = roots.get(reference.root());
        for (String name : reference.path()) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(name)) {
                throw failure(
                    "missing program value: " + reference.root() + "." + String.join(".", reference.path()),
                    reference.span()
                );
            }
            current = map.get(name);
        }
        if (current == null) {
            if (reference.nullable() || reference.type() == LanguageType.Scalar.NULL) return null;
            throw failure("non-nullable program value is null", reference.span());
        }
        if (!matchesType(current, reference.type())) throw failure(
            "program input has an incompatible type",
            reference.span()
        );
        return current;
    }

    static boolean matchesType(@Nullable Object value, LanguageType type) {
        return switch (type) {
            case LanguageType.Scalar scalar -> switch (scalar) {
                case BOOL -> value instanceof Boolean;
                case STRING -> value instanceof String;
                case NUMBER -> value instanceof Number number && finite(number);
                case NULL -> value == null;
            };
            case LanguageType.ListType list -> value instanceof List<?> values &&
                values.stream().allMatch(item -> matchesType(item, list.elementType()));
            case LanguageType.StructuredType structured -> value instanceof Map<?, ?> map &&
                structured
                    .fields()
                    .entrySet()
                    .stream()
                    .allMatch(entry -> {
                        Object nested = map.get(entry.getKey());
                        return nested != null
                            ? matchesType(nested, entry.getValue().type())
                            : entry.getValue().nullable() || entry.getValue().type() == LanguageType.Scalar.NULL;
                    });
        };
    }

    private static boolean conforms(@Nullable Object value, LanguageType type, boolean nullable) {
        return value == null ? nullable || type == LanguageType.Scalar.NULL : matchesType(value, type);
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
        if (!(value instanceof Boolean result)) throw new IllegalArgumentException(
            "Embedded Language value is not Bool"
        );
        return result;
    }

    private static BigDecimal number(@Nullable Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException(
            "Embedded Language value is not Number"
        );
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
        return left.divide(right, java.math.MathContext.DECIMAL128);
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
