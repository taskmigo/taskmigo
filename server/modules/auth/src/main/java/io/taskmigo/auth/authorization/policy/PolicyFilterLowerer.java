package io.taskmigo.auth.authorization.policy;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.filter.FilterAst;
import io.taskmigo.policy.PartialPolicy;
import io.taskmigo.policy.PolicyIr;
import io.taskmigo.policy.PolicyPartialEvaluator;
import java.util.Map;
import org.springframework.stereotype.Service;

/// Lowers generic Policy Language residuals into the authorization database Filter AST.
@Service
public final class PolicyFilterLowerer {

    private final PolicyPartialEvaluator partialEvaluator;

    /// Creates a query adapter backed by the generic partial evaluator.
    public PolicyFilterLowerer(PolicyPartialEvaluator partialEvaluator) {
        this.partialEvaluator = partialEvaluator;
    }

    /// Partially evaluates and lowers an object policy using known operation roots.
    public FilterAst partial(PolicyIr policy, Map<String, ?> roots) {
        PartialPolicy partial = this.partialEvaluator.partial(policy, roots);
        if (partial.isConcrete()) {
            return new FilterAst(Boolean.TRUE.equals(partial.value()) ? FilterAst.all() : FilterAst.none());
        }
        try {
            if (!(partial.residual() instanceof PolicyIr.Expression residual)) {
                throw invalid("partial evaluation produced no residual expression");
            }
            return new FilterAst(this.filter(residual));
        } catch (AuthorizationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthorizationException("Invalid Object authorization policy: " + exception.getMessage());
        }
    }

    private FilterAst.Expression filter(PolicyIr.Expression expression) {
        return switch (expression) {
            case PolicyIr.Literal literal -> new FilterAst.Literal(literal.value());
            case PolicyIr.Reference reference -> objectField(reference);
            case PolicyIr.ListLiteral _ -> throw invalid("list expressions are not queryable");
            case PolicyIr.Unary unary when unary.operator() == PolicyIr.UnaryOperator.NOT -> new FilterAst.Unary(
                FilterAst.Operator.NOT,
                filter(unary.operand())
            );
            case PolicyIr.Unary unary when unary.operator() == PolicyIr.UnaryOperator.MINUS -> new FilterAst.Unary(
                FilterAst.Operator.NEGATE,
                filter(unary.operand())
            );
            case PolicyIr.Unary _ -> throw invalid("unary arithmetic is not queryable");
            case PolicyIr.Binary binary -> binary(binary);
            case PolicyIr.Conditional conditional -> FilterAst.or(
                FilterAst.and(filter(conditional.condition()), filter(conditional.whenTrue())),
                FilterAst.and(FilterAst.not(filter(conditional.condition())), filter(conditional.whenFalse()))
            );
        };
    }

    private FilterAst.Expression binary(PolicyIr.Binary expression) {
        FilterAst.Operator operator = switch (expression.operator()) {
            case AND -> FilterAst.Operator.AND;
            case OR -> FilterAst.Operator.OR;
            case EQUAL -> FilterAst.Operator.EQ;
            case NOT_EQUAL -> FilterAst.Operator.NE;
            case GREATER -> FilterAst.Operator.GT;
            case GREATER_OR_EQUAL -> FilterAst.Operator.GE;
            case LESS -> FilterAst.Operator.LT;
            case LESS_OR_EQUAL -> FilterAst.Operator.LE;
            case ADD -> FilterAst.Operator.ADD;
            case SUBTRACT -> FilterAst.Operator.SUBTRACT;
            case MULTIPLY -> FilterAst.Operator.MULTIPLY;
            case DIVIDE -> FilterAst.Operator.DIVIDE;
            case IN, MODULO -> throw invalid(
                "operator is not queryable: " + expression.operator().name().toLowerCase()
            );
        };
        return new FilterAst.Binary(operator, filter(expression.left()), filter(expression.right()));
    }

    private static FilterAst.Field objectField(PolicyIr.Reference reference) {
        if (!reference.root().equals("object") || reference.path().size() != 1) {
            throw invalid("only direct object fields are queryable");
        }
        return new FilterAst.Field(reference.path().getFirst());
    }

    private static AuthorizationException invalid(String message) {
        return new AuthorizationException("Invalid Object authorization policy: " + message);
    }
}
