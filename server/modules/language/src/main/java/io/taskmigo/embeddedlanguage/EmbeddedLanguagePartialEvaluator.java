package io.taskmigo.embeddedlanguage;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Partially evaluates Semantic AST using only the roots known to the consumer.
@SuppressWarnings("checkstyle:NeedBraces")
public final class EmbeddedLanguagePartialEvaluator {

    /// Produces a concrete typed value or a typed residual Semantic AST expression.
    public PartialProgram partial(SemanticAst program, Map<String, ?> knownRoots) {
        Objects.requireNonNull(program);
        Objects.requireNonNull(knownRoots);
        requireSymbolicUnknowns(program.expression(), knownRoots.keySet());
        SemanticAst.Expression residual = simplify(program.expression(), knownRoots);
        if (residual instanceof SemanticAst.Literal literal) {
            if (!conforms(literal.value(), program)) throw incompatible(residual.span());
            return new PartialProgram.Concrete(literal.value(), program.resultType());
        }
        if (!residual.type().equals(program.resultType())) throw incompatible(residual.span());
        if (residual.nullable() && !program.resultNullable()) throw incompatible(residual.span());
        return new PartialProgram.Residual(residual);
    }

    private static SemanticAst.Expression simplify(SemanticAst.Expression expression, Map<String, ?> knownRoots) {
        if (Collections.disjoint(expression.dependencies(), knownRoots.keySet())) return expression;
        return switch (expression) {
            case SemanticAst.Literal literal -> literal;
            case SemanticAst.Reference reference when !knownRoots.containsKey(reference.root()) -> reference;
            case SemanticAst.Reference reference -> {
                @Nullable
                Object value = EmbeddedLanguageEvaluator.read(reference, knownRoots);
                yield literal(value, reference.type(), reference.span());
            }
            case SemanticAst.ListLiteral list -> list(list, knownRoots);
            case SemanticAst.Unary unary -> unary(unary, knownRoots);
            case SemanticAst.Binary binary -> binary(binary, knownRoots);
            case SemanticAst.Conditional conditional -> conditional(conditional, knownRoots);
            case SemanticAst.Quantifier quantifier -> quantifier(quantifier, knownRoots);
            case SemanticAst.Length length -> length(length, knownRoots);
        };
    }

    private static void requireSymbolicUnknowns(SemanticAst.Expression expression, Set<String> knownRoots) {
        switch (expression) {
            case SemanticAst.Literal _ -> {
            }
            case SemanticAst.Reference reference -> {
                if (
                    !knownRoots.contains(reference.root()) &&
                    !reference.symbolic() &&
                    !reference.root().equals("__lambda__")
                ) {
                    throw new EmbeddedLanguageException(
                        new LanguageDiagnostic(
                            LanguageDiagnostic.Category.TypeError,
                            "program reference may not remain symbolic: " +
                                reference.root() +
                                (reference.path().isEmpty() ? "" : "." + String.join(".", reference.path())),
                            reference.span()
                        )
                    );
                }
            }
            case SemanticAst.ListLiteral list -> list.values().forEach(value ->
                requireSymbolicUnknowns(value, knownRoots)
            );
            case SemanticAst.Unary unary -> requireSymbolicUnknowns(unary.operand(), knownRoots);
            case SemanticAst.Binary binary -> {
                requireSymbolicUnknowns(binary.left(), knownRoots);
                requireSymbolicUnknowns(binary.right(), knownRoots);
            }
            case SemanticAst.Conditional conditional -> {
                requireSymbolicUnknowns(conditional.condition(), knownRoots);
                requireSymbolicUnknowns(conditional.whenTrue(), knownRoots);
                requireSymbolicUnknowns(conditional.whenFalse(), knownRoots);
            }
            case SemanticAst.Quantifier quantifier -> {
                requireSymbolicUnknowns(quantifier.collection(), knownRoots);
                requireSymbolicUnknowns(quantifier.predicate(), knownRoots);
            }
            case SemanticAst.Length length -> requireSymbolicUnknowns(length.operand(), knownRoots);
        }
    }

    private static SemanticAst.Expression list(SemanticAst.ListLiteral expression, Map<String, ?> roots) {
        List<SemanticAst.Expression> values = expression
            .values()
            .stream()
            .map(value -> simplify(value, roots))
            .toList();
        if (values.stream().allMatch(EmbeddedLanguagePartialEvaluator::literalValue)) {
            return literal(
                values
                    .stream()
                    .map(value -> ((SemanticAst.Literal) value).value())
                    .toList(),
                expression.type(),
                expression.span()
            );
        }
        return new SemanticAst.ListLiteral(values, expression.type(), dependencies(values), expression.span());
    }

    private static SemanticAst.Expression unary(SemanticAst.Unary expression, Map<String, ?> roots) {
        SemanticAst.Expression operand = simplify(expression.operand(), roots);
        return operand instanceof SemanticAst.Literal literal
            ? literal(EmbeddedLanguageEvaluator.compute(expression.operator(), literal.value()), expression.span())
            : new SemanticAst.Unary(
                  expression.operator(),
                  operand,
                  expression.type(),
                  operand.dependencies(),
                  expression.span()
              );
    }

    private static SemanticAst.Expression binary(SemanticAst.Binary expression, Map<String, ?> roots) {
        SemanticAst.Expression left = simplify(expression.left(), roots);
        if (
            expression.operator() == SemanticAst.BinaryOperator.AND &&
            left instanceof SemanticAst.Literal literal &&
            literal.value() instanceof Boolean value
        ) {
            if (!value) return literal(false, expression.span());
            return simplify(expression.right(), roots);
        }
        if (
            expression.operator() == SemanticAst.BinaryOperator.OR &&
            left instanceof SemanticAst.Literal literal &&
            literal.value() instanceof Boolean value
        ) {
            if (value) return literal(true, expression.span());
            return simplify(expression.right(), roots);
        }
        SemanticAst.Expression right = simplify(expression.right(), roots);
        if (
            left instanceof SemanticAst.Literal leftLiteral && right instanceof SemanticAst.Literal rightLiteral
        ) return literal(
            EmbeddedLanguageEvaluator.compute(expression.operator(), leftLiteral.value(), rightLiteral.value()),
            expression.span()
        );
        return new SemanticAst.Binary(
            expression.operator(),
            left,
            right,
            expression.type(),
            union(left, right),
            expression.span()
        );
    }

    private static SemanticAst.Expression conditional(SemanticAst.Conditional expression, Map<String, ?> roots) {
        SemanticAst.Expression condition = simplify(expression.condition(), roots);
        if (
            condition instanceof SemanticAst.Literal literal && literal.value() instanceof Boolean value
        ) return simplify(value ? expression.whenTrue() : expression.whenFalse(), roots);
        SemanticAst.Expression whenTrue = simplify(expression.whenTrue(), roots);
        SemanticAst.Expression whenFalse = simplify(expression.whenFalse(), roots);
        return new SemanticAst.Conditional(
            condition,
            whenTrue,
            whenFalse,
            expression.type(),
            union(condition, whenTrue, whenFalse),
            expression.span()
        );
    }

    private static SemanticAst.Expression quantifier(SemanticAst.Quantifier expression, Map<String, ?> roots) {
        SemanticAst.Expression collection = simplify(expression.collection(), roots);
        if (collection instanceof SemanticAst.Literal literal && literal.value() instanceof List<?> values) {
            for (Object element : values) {
                Map<String, Object> scoped = new HashMap<>(roots);
                Map<String, Object> binding = new HashMap<>();
                binding.put(expression.elementName(), element);
                scoped.put("__lambda__", binding);
                SemanticAst.Expression predicate = simplify(expression.predicate(), scoped);
                if (predicate instanceof SemanticAst.Literal result && result.value() instanceof Boolean matches) {
                    if (expression.operator() == SemanticAst.QuantifierOperator.ALL && !matches) return literal(
                        false,
                        expression.span()
                    );
                    if (expression.operator() == SemanticAst.QuantifierOperator.ANY && matches) return literal(
                        true,
                        expression.span()
                    );
                    if (expression.operator() == SemanticAst.QuantifierOperator.NONE && matches) return literal(
                        false,
                        expression.span()
                    );
                } else {
                    return new SemanticAst.Quantifier(
                        expression.operator(),
                        collection,
                        expression.elementName(),
                        predicate,
                        LanguageType.Scalar.BOOL,
                        union(collection, predicate),
                        expression.span()
                    );
                }
            }
            return literal(expression.operator() != SemanticAst.QuantifierOperator.ANY, expression.span());
        }
        SemanticAst.Expression predicate = simplify(expression.predicate(), roots);
        return new SemanticAst.Quantifier(
            expression.operator(),
            collection,
            expression.elementName(),
            predicate,
            LanguageType.Scalar.BOOL,
            union(collection, predicate),
            expression.span()
        );
    }

    private static SemanticAst.Expression length(SemanticAst.Length expression, Map<String, ?> roots) {
        SemanticAst.Expression operand = simplify(expression.operand(), roots);
        if (operand instanceof SemanticAst.Literal literal) {
            if (literal.value() instanceof String text) return literal(
                java.math.BigDecimal.valueOf(text.length()),
                expression.span()
            );
            if (literal.value() instanceof List<?> list) return literal(
                java.math.BigDecimal.valueOf(list.size()),
                expression.span()
            );
        }
        return new SemanticAst.Length(operand, expression.type(), operand.dependencies(), expression.span());
    }

    private static boolean literalValue(SemanticAst.Expression expression) {
        return expression instanceof SemanticAst.Literal;
    }

    private static SemanticAst.Expression literal(
        @Nullable Object value,
        LanguageType type,
        LanguageDiagnostic.SourceSpan span
    ) {
        return new SemanticAst.Literal(value, type, Set.of(), span);
    }

    private static SemanticAst.Expression literal(@Nullable Object value, LanguageDiagnostic.SourceSpan span) {
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

    private static boolean conforms(@Nullable Object value, SemanticAst program) {
        if (value == null) return program.resultNullable() || program.resultType() == LanguageType.Scalar.NULL;
        return EmbeddedLanguageEvaluator.matchesType(value, program.resultType());
    }

    private static EmbeddedLanguageException incompatible(LanguageDiagnostic.SourceSpan span) {
        return new EmbeddedLanguageException(
            new LanguageDiagnostic(
                LanguageDiagnostic.Category.TypeError,
                "partial result has an incompatible type",
                span
            )
        );
    }

    private static Set<String> dependencies(Iterable<SemanticAst.Expression> values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
            .flatMap(value -> value.dependencies().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> union(SemanticAst.Expression... values) {
        return java.util.Arrays.stream(values)
            .flatMap(value -> value.dependencies().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
