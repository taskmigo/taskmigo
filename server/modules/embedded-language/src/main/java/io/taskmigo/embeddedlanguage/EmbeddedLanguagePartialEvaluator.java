package io.taskmigo.embeddedlanguage;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Partially evaluates policies using only the roots known to the consumer.
@SuppressWarnings("checkstyle:NeedBraces")
public final class EmbeddedLanguagePartialEvaluator {

    /// Produces a concrete boolean or a typed residual expression.
    public PartialProgram partial(LanguageIr program, Map<String, ?> knownRoots) {
        Objects.requireNonNull(program);
        Objects.requireNonNull(knownRoots);
        LanguageIr.Expression residual = simplify(program.expression(), knownRoots);
        if (
            residual instanceof LanguageIr.Literal literal && literal.value() instanceof Boolean value
        ) return new PartialProgram(value, (LanguageIr.@Nullable Expression) null);
        if (!residual.type().equals(LanguageType.Scalar.BOOL)) throw new EmbeddedLanguageException(
            new LanguageDiagnostic(LanguageDiagnostic.Category.TypeError, "partial result is not Bool", residual.span())
        );
        return new PartialProgram(null, residual);
    }

    private static LanguageIr.Expression simplify(LanguageIr.Expression expression, Map<String, ?> knownRoots) {
        return switch (expression) {
            case LanguageIr.Literal literal -> literal;
            case LanguageIr.Reference reference when !knownRoots.containsKey(reference.root()) -> reference;
            case LanguageIr.Reference reference -> {
                Object value = EmbeddedLanguageEvaluator.read(reference, knownRoots);
                yield literal(value, value == null ? LanguageType.Scalar.NULL : reference.type(), reference.span());
            }
            case LanguageIr.ListLiteral list -> list(list, knownRoots);
            case LanguageIr.Unary unary -> unary(unary, knownRoots);
            case LanguageIr.Binary binary -> binary(binary, knownRoots);
            case LanguageIr.Conditional conditional -> conditional(conditional, knownRoots);
        };
    }

    private static LanguageIr.Expression list(LanguageIr.ListLiteral expression, Map<String, ?> roots) {
        List<LanguageIr.Expression> values = expression
            .values()
            .stream()
            .map(value -> simplify(value, roots))
            .toList();
        if (values.stream().allMatch(EmbeddedLanguagePartialEvaluator::literalValue)) {
            return literal(
                values
                    .stream()
                    .map(value -> ((LanguageIr.Literal) value).value())
                    .toList(),
                expression.type(),
                expression.span()
            );
        }
        return new LanguageIr.ListLiteral(values, expression.type(), dependencies(values), expression.span());
    }

    private static LanguageIr.Expression unary(LanguageIr.Unary expression, Map<String, ?> roots) {
        LanguageIr.Expression operand = simplify(expression.operand(), roots);
        return operand instanceof LanguageIr.Literal literal
            ? literal(EmbeddedLanguageEvaluator.compute(expression.operator(), literal.value()), expression.span())
            : new LanguageIr.Unary(
                  expression.operator(),
                  operand,
                  expression.type(),
                  operand.dependencies(),
                  expression.span()
              );
    }

    private static LanguageIr.Expression binary(LanguageIr.Binary expression, Map<String, ?> roots) {
        LanguageIr.Expression left = simplify(expression.left(), roots);
        if (
            expression.operator() == LanguageIr.BinaryOperator.AND &&
            left instanceof LanguageIr.Literal literal &&
            literal.value() instanceof Boolean value
        ) {
            if (!value) return literal(false, expression.span());
            return simplify(expression.right(), roots);
        }
        if (
            expression.operator() == LanguageIr.BinaryOperator.OR &&
            left instanceof LanguageIr.Literal literal &&
            literal.value() instanceof Boolean value
        ) {
            if (value) return literal(true, expression.span());
            return simplify(expression.right(), roots);
        }
        LanguageIr.Expression right = simplify(expression.right(), roots);
        if (
            left instanceof LanguageIr.Literal leftLiteral && right instanceof LanguageIr.Literal rightLiteral
        ) return literal(
            EmbeddedLanguageEvaluator.compute(expression.operator(), leftLiteral.value(), rightLiteral.value()),
            expression.span()
        );
        return new LanguageIr.Binary(
            expression.operator(),
            left,
            right,
            expression.type(),
            union(left, right),
            expression.span()
        );
    }

    private static LanguageIr.Expression conditional(LanguageIr.Conditional expression, Map<String, ?> roots) {
        LanguageIr.Expression condition = simplify(expression.condition(), roots);
        if (
            condition instanceof LanguageIr.Literal literal && literal.value() instanceof Boolean value
        ) return simplify(value ? expression.whenTrue() : expression.whenFalse(), roots);
        LanguageIr.Expression whenTrue = simplify(expression.whenTrue(), roots);
        LanguageIr.Expression whenFalse = simplify(expression.whenFalse(), roots);
        return new LanguageIr.Conditional(
            condition,
            whenTrue,
            whenFalse,
            expression.type(),
            union(condition, whenTrue, whenFalse),
            expression.span()
        );
    }

    private static boolean literalValue(LanguageIr.Expression expression) {
        return expression instanceof LanguageIr.Literal;
    }

    private static LanguageIr.Expression literal(
        @Nullable Object value,
        LanguageType type,
        LanguageDiagnostic.SourceSpan span
    ) {
        return new LanguageIr.Literal(value, type, Set.of(), span);
    }

    private static LanguageIr.Expression literal(@Nullable Object value, LanguageDiagnostic.SourceSpan span) {
        return literal(value, typeOf(value), span);
    }

    private static LanguageType typeOf(@Nullable Object value) {
        return switch (value) {
            case null -> LanguageType.Scalar.NULL;
            case Boolean _ -> LanguageType.Scalar.BOOL;
            case String _ -> LanguageType.Scalar.STRING;
            case Number _ -> LanguageType.Scalar.NUMBER;
            case List<?> list when list.isEmpty() -> new LanguageType.ListType(LanguageType.Scalar.NULL);
            case List<?> list -> {
                LanguageType element = typeOf(list.getFirst());
                if (
                    list
                        .stream()
                        .map(EmbeddedLanguagePartialEvaluator::typeOf)
                        .anyMatch(type -> !type.equals(element))
                ) {
                    throw new IllegalArgumentException("unsupported heterogeneous Embedded Language list");
                }
                yield new LanguageType.ListType(element);
            }
            default -> throw new IllegalArgumentException("unsupported Embedded Language value");
        };
    }

    private static Set<String> dependencies(Iterable<LanguageIr.Expression> values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
            .flatMap(value -> value.dependencies().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> union(LanguageIr.Expression... values) {
        return java.util.Arrays.stream(values)
            .flatMap(value -> value.dependencies().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
