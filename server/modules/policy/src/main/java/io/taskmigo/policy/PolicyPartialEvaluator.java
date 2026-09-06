package io.taskmigo.policy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Partially evaluates policies using only the roots known to the consumer.
@SuppressWarnings("checkstyle:NeedBraces")
public final class PolicyPartialEvaluator {

    /// Produces a concrete boolean or a typed residual expression.
    public PartialPolicy partial(PolicyIr policy, Map<String, ?> knownRoots) {
        PolicyIr.Expression residual = simplify(policy.expression(), knownRoots);
        if (
            residual instanceof PolicyIr.Literal literal && literal.value() instanceof Boolean value
        ) return new PartialPolicy(value, (PolicyIr.@Nullable Expression) null);
        return new PartialPolicy(null, residual);
    }

    private static PolicyIr.Expression simplify(PolicyIr.Expression expression, Map<String, ?> knownRoots) {
        return switch (expression) {
            case PolicyIr.Literal literal -> literal;
            case PolicyIr.Reference reference when !knownRoots.containsKey(reference.root()) -> reference;
            case PolicyIr.Reference reference -> literal(
                PolicyEvaluatorValue.read(reference, knownRoots),
                reference.span()
            );
            case PolicyIr.ListLiteral list -> list(list, knownRoots);
            case PolicyIr.Unary unary -> unary(unary, knownRoots);
            case PolicyIr.Binary binary -> binary(binary, knownRoots);
            case PolicyIr.Conditional conditional -> conditional(conditional, knownRoots);
        };
    }

    private static PolicyIr.Expression list(PolicyIr.ListLiteral expression, Map<String, ?> roots) {
        List<PolicyIr.Expression> values = expression
            .values()
            .stream()
            .map(value -> simplify(value, roots))
            .toList();
        if (values.stream().allMatch(PolicyPartialEvaluator::literalValue)) {
            return literal(
                values
                    .stream()
                    .map(value -> ((PolicyIr.Literal) value).value())
                    .toList(),
                expression.span()
            );
        }
        return new PolicyIr.ListLiteral(values, expression.type(), dependencies(values), expression.span());
    }

    private static PolicyIr.Expression unary(PolicyIr.Unary expression, Map<String, ?> roots) {
        PolicyIr.Expression operand = simplify(expression.operand(), roots);
        return operand instanceof PolicyIr.Literal literal
            ? literal(PolicyEvaluator.compute(expression.operator(), literal.value()), expression.span())
            : new PolicyIr.Unary(
                  expression.operator(),
                  operand,
                  expression.type(),
                  operand.dependencies(),
                  expression.span()
              );
    }

    private static PolicyIr.Expression binary(PolicyIr.Binary expression, Map<String, ?> roots) {
        PolicyIr.Expression left = simplify(expression.left(), roots);
        if (
            expression.operator() == PolicyIr.BinaryOperator.AND &&
            left instanceof PolicyIr.Literal literal &&
            literal.value() instanceof Boolean value
        ) {
            if (!value) return literal(false, expression.span());
            return simplify(expression.right(), roots);
        }
        if (
            expression.operator() == PolicyIr.BinaryOperator.OR &&
            left instanceof PolicyIr.Literal literal &&
            literal.value() instanceof Boolean value
        ) {
            if (value) return literal(true, expression.span());
            return simplify(expression.right(), roots);
        }
        PolicyIr.Expression right = simplify(expression.right(), roots);
        if (
            left instanceof PolicyIr.Literal leftLiteral && right instanceof PolicyIr.Literal rightLiteral
        ) return literal(
            PolicyEvaluator.compute(expression.operator(), leftLiteral.value(), rightLiteral.value()),
            expression.span()
        );
        return new PolicyIr.Binary(
            expression.operator(),
            left,
            right,
            expression.type(),
            union(left, right),
            expression.span()
        );
    }

    private static PolicyIr.Expression conditional(PolicyIr.Conditional expression, Map<String, ?> roots) {
        PolicyIr.Expression condition = simplify(expression.condition(), roots);
        if (condition instanceof PolicyIr.Literal literal && literal.value() instanceof Boolean value) return simplify(
            value ? expression.whenTrue() : expression.whenFalse(),
            roots
        );
        return new PolicyIr.Conditional(
            condition,
            simplify(expression.whenTrue(), roots),
            simplify(expression.whenFalse(), roots),
            expression.type(),
            union(condition, expression.whenTrue(), expression.whenFalse()),
            expression.span()
        );
    }

    private static boolean literalValue(PolicyIr.Expression expression) {
        return expression instanceof PolicyIr.Literal;
    }

    private static PolicyIr.Expression literal(@Nullable Object value, PolicyDiagnostic.SourceSpan span) {
        return new PolicyIr.Literal(value, typeOf(value), Set.of(), span);
    }

    private static PolicyType typeOf(@Nullable Object value) {
        return switch (value) {
            case null -> PolicyType.Scalar.NULL;
            case Boolean _ -> PolicyType.Scalar.BOOL;
            case String _ -> PolicyType.Scalar.STRING;
            case Number _ -> PolicyType.Scalar.NUMBER;
            case List<?> _ -> new PolicyType.ListType(PolicyType.Scalar.STRING);
            default -> throw new IllegalArgumentException("unsupported Policy Language value");
        };
    }

    private static Set<String> dependencies(Iterable<PolicyIr.Expression> values) {
        return java.util.stream.StreamSupport.stream(values.spliterator(), false)
            .flatMap(value -> value.dependencies().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<String> union(PolicyIr.Expression... values) {
        return java.util.Arrays.stream(values)
            .flatMap(value -> value.dependencies().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static final class PolicyEvaluatorValue {

        private static @Nullable Object read(PolicyIr.Reference reference, Map<String, ?> roots) {
            Object current = roots.get(reference.root());
            for (String name : reference.path()) {
                if (!(current instanceof Map<?, ?> map) || !map.containsKey(name)) throw new PolicyException(
                    new PolicyDiagnostic(
                        PolicyDiagnostic.Category.TypeError,
                        "missing known policy value",
                        reference.span()
                    )
                );
                current = map.get(name);
            }
            return current;
        }
    }
}
