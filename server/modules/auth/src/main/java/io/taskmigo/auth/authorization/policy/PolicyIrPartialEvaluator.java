package io.taskmigo.auth.authorization.policy;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.filter.FilterAst;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/// Specializes known Policy IR roots and converts the remaining object predicate into Filter AST.
@Service
public final class PolicyIrPartialEvaluator {

    private static final Object UNKNOWN = new Object();

    private static final JavaScriptPolicyEvaluator EVALUATOR = new JavaScriptPolicyEvaluator();

    /// Partially evaluates a compiled Object policy against known request and principal values.
    ///
    /// Object references remain symbolic fields. Any residual expression outside the supported Filter AST is
    /// rejected so callers cannot fall back to per-row JVM authorization.
    ///
    /// @param policy the compiled Object policy
    /// @param roots the immutable operation roots containing known request and principal values
    /// @return a database-oriented residual filter
    /// @throws AuthorizationException when the policy cannot be represented by the Filter AST
    public FilterAst partial(PolicyIr policy, Map<String, ?> roots) {
        return new FilterAst(this.filter(this.specialize(policy.expression(), roots)));
    }

    private PolicyIr.Expression specialize(PolicyIr.Expression expression, Map<String, ?> roots) {
        return switch (expression) {
            case PolicyIr.Literal literal -> literal;
            case PolicyIr.UndefinedValue undefined -> undefined;
            case PolicyIr.Reference reference when reference.root().equals("object") -> reference;
            case PolicyIr.Reference reference -> new PolicyIr.Literal(resolve(reference, roots));
            case PolicyIr.PropertyAccess property -> this.specializeProperty(property, roots);
            case PolicyIr.ArrayValue array -> new PolicyIr.ArrayValue(array.values().stream()
                .map(value -> this.specialize(value, roots))
                .toList());
            case PolicyIr.ObjectValue object -> new PolicyIr.ObjectValue(object.values().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> this.specialize(entry.getValue(), roots),
                    (first, second) -> first,
                    LinkedHashMap::new
                )));
            case PolicyIr.Binary binary -> this.specializeBinary(binary, roots);
            case PolicyIr.Unary unary -> this.specializeUnary(unary, roots);
            case PolicyIr.Conditional conditional -> {
                PolicyIr.Expression condition = this.specialize(conditional.condition(), roots);
                if (condition instanceof PolicyIr.Literal literal) {
                    yield truthy(literal.value())
                        ? this.specialize(conditional.whenTrue(), roots)
                        : this.specialize(conditional.whenFalse(), roots);
                }
                yield new PolicyIr.Conditional(
                    condition,
                    this.specialize(conditional.whenTrue(), roots),
                    this.specialize(conditional.whenFalse(), roots)
                );
            }
        };
    }

    private PolicyIr.Expression specializeProperty(PolicyIr.PropertyAccess property, Map<String, ?> roots) {
        PolicyIr.Expression target = this.specialize(property.target(), roots);
        Object value = constantValue(new PolicyIr.PropertyAccess(target, property.property()));
        return value == UNKNOWN ? new PolicyIr.PropertyAccess(target, property.property()) : new PolicyIr.Literal(value);
    }

    private PolicyIr.Expression specializeBinary(PolicyIr.Binary binary, Map<String, ?> roots) {
        PolicyIr.Expression left = this.specialize(binary.left(), roots);
        PolicyIr.Expression right = this.specialize(binary.right(), roots);
        if (binary.operator() == PolicyIr.BinaryOperator.AND && left instanceof PolicyIr.Literal literal) {
            if (!truthy(literal.value())) return literal;
            return right;
        }
        if (binary.operator() == PolicyIr.BinaryOperator.OR && left instanceof PolicyIr.Literal literal) {
            if (truthy(literal.value())) return literal;
            return right;
        }
        PolicyIr.Binary specialized = new PolicyIr.Binary(binary.operator(), left, right);
        Object value = constantValue(specialized);
        return value == UNKNOWN ? specialized : new PolicyIr.Literal(value);
    }

    private PolicyIr.Expression specializeUnary(PolicyIr.Unary unary, Map<String, ?> roots) {
        PolicyIr.Expression operand = this.specialize(unary.operand(), roots);
        Object value = constantValue(new PolicyIr.Unary(unary.operator(), operand));
        return value == UNKNOWN ? new PolicyIr.Unary(unary.operator(), operand) : new PolicyIr.Literal(value);
    }

    private FilterAst.Expression filter(PolicyIr.Expression expression) {
        return switch (expression) {
            case PolicyIr.Literal literal -> new FilterAst.Literal(literal.value());
            case PolicyIr.UndefinedValue ignored -> throw invalid("undefined is not a database filter value");
            case PolicyIr.Reference reference -> objectField(reference);
            case PolicyIr.PropertyAccess ignored -> throw invalid("computed properties are not queryable");
            case PolicyIr.ArrayValue array -> new FilterAst.Literal(array.values().stream()
                .map(PolicyIrPartialEvaluator::constantRequired)
                .toList());
            case PolicyIr.ObjectValue ignored -> throw invalid("object literals are not queryable");
            case PolicyIr.Binary binary -> binary(binary);
            case PolicyIr.Unary unary when unary.operator() == PolicyIr.UnaryOperator.NOT
                && unary.operand() instanceof PolicyIr.Binary binary
                && binary.operator() == PolicyIr.BinaryOperator.IN -> new FilterAst.Binary(
                    FilterAst.Operator.NIN,
                    this.filter(binary.left()),
                    this.filter(binary.right())
                );
            case PolicyIr.Unary unary when unary.operator() == PolicyIr.UnaryOperator.NOT -> new FilterAst.Unary(
                FilterAst.Operator.NOT,
                this.filter(unary.operand())
            );
            case PolicyIr.Unary ignored -> throw invalid("arithmetic residuals are not queryable");
            case PolicyIr.Conditional ignored -> throw invalid("conditional residuals are not queryable");
        };
    }

    private FilterAst.Expression binary(PolicyIr.Binary binary) {
        if (binary.operator() == PolicyIr.BinaryOperator.CONTAINS
            && binary.left() instanceof PolicyIr.ArrayValue array
            && binary.right() instanceof PolicyIr.Reference reference) {
            return new FilterAst.Binary(
                FilterAst.Operator.IN,
                objectField(reference),
                new FilterAst.Literal(array.values().stream()
                    .map(PolicyIrPartialEvaluator::constantRequired)
                    .toList())
            );
        }
        FilterAst.Operator operator = switch (binary.operator()) {
            case AND -> FilterAst.Operator.AND;
            case OR -> FilterAst.Operator.OR;
            case EQUAL -> FilterAst.Operator.EQ;
            case NOT_EQUAL -> FilterAst.Operator.NE;
            case GREATER -> FilterAst.Operator.GT;
            case GREATER_OR_EQUAL -> FilterAst.Operator.GE;
            case LESS -> FilterAst.Operator.LT;
            case LESS_OR_EQUAL -> FilterAst.Operator.LE;
            case IN -> FilterAst.Operator.IN;
            case CONTAINS -> FilterAst.Operator.CONTAINS;
            case STARTS_WITH -> FilterAst.Operator.STARTS_WITH;
            case ENDS_WITH -> FilterAst.Operator.ENDS_WITH;
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> throw invalid(
                "arithmetic residuals are not queryable"
            );
        };
        if ((operator == FilterAst.Operator.EQ || operator == FilterAst.Operator.NE)
            && isUndefined(binary.right()) && binary.left() instanceof PolicyIr.Reference reference) {
            FilterAst.Expression field = objectField(reference);
            return operator == FilterAst.Operator.EQ
                ? new FilterAst.Unary(FilterAst.Operator.NOT, new FilterAst.Unary(FilterAst.Operator.PRESENT, field))
                : new FilterAst.Unary(FilterAst.Operator.PRESENT, field);
        }
        return new FilterAst.Binary(operator, this.filter(binary.left()), this.filter(binary.right()));
    }

    private static FilterAst.Field objectField(PolicyIr.Reference reference) {
        if (!reference.root().equals("object") || reference.path().size() != 1) throw invalid(
            "only direct object fields are queryable"
        );
        return new FilterAst.Field(reference.path().getFirst());
    }

    private static boolean isUndefined(PolicyIr.Expression expression) {
        return expression instanceof PolicyIr.UndefinedValue;
    }

    private static Object resolve(PolicyIr.Reference reference, Map<String, ?> roots) {
        if (!roots.containsKey(reference.root())) throw invalid(
            "missing authorization context value: " + reference.root()
        );
        @Nullable Object current = roots.get(reference.root());
        for (String part : reference.path()) {
            if (!(current instanceof Map<?, ?> values) || !values.containsKey(part)) throw invalid(
                "missing authorization context value: " + reference.root() + "." + part
            );
            current = values.get(part);
        }
        return Objects.requireNonNull(current);
    }

    private static @Nullable Object constantRequired(PolicyIr.Expression expression) {
        Object value = constantValue(expression);
        if (value == UNKNOWN || value == null || value == JavaScriptPolicyEvaluator.undefinedValue()) throw invalid(
            "object-dependent collection values are not queryable"
        );
        return value;
    }

    private static @Nullable Object constantValue(PolicyIr.Expression expression) {
        return containsReference(expression)
            ? UNKNOWN
            : EVALUATOR.evaluateValue(expression, Map.of());
    }

    private static boolean containsReference(PolicyIr.Expression expression) {
        return switch (expression) {
            case PolicyIr.Literal ignored -> false;
            case PolicyIr.UndefinedValue ignored -> false;
            case PolicyIr.Reference ignored -> true;
            case PolicyIr.PropertyAccess property -> containsReference(property.target());
            case PolicyIr.ArrayValue array -> array.values().stream().anyMatch(PolicyIrPartialEvaluator::containsReference);
            case PolicyIr.ObjectValue object -> object.values().values().stream()
                .anyMatch(PolicyIrPartialEvaluator::containsReference);
            case PolicyIr.Binary binary -> containsReference(binary.left()) || containsReference(binary.right());
            case PolicyIr.Unary unary -> containsReference(unary.operand());
            case PolicyIr.Conditional conditional -> containsReference(conditional.condition())
                || containsReference(conditional.whenTrue())
                || containsReference(conditional.whenFalse());
        };
    }

    private static boolean truthy(@Nullable Object value) {
        if (value == null || value == JavaScriptPolicyEvaluator.undefinedValue()) return false;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0 && !Double.isNaN(number.doubleValue());
        return !(value instanceof String string) || !string.isEmpty();
    }

    private static AuthorizationException invalid(String message) {
        return new AuthorizationException("Invalid Object authorization policy: " + message);
    }
}
