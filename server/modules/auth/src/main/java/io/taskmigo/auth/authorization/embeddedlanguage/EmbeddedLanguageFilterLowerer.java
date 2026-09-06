package io.taskmigo.auth.authorization.embeddedlanguage;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.filter.FilterAst;
import io.taskmigo.embeddedlanguage.EmbeddedLanguagePartialEvaluator;
import io.taskmigo.embeddedlanguage.LanguageIr;
import io.taskmigo.embeddedlanguage.PartialProgram;
import java.util.Map;
import org.springframework.stereotype.Service;

/// Lowers generic Embedded Language residuals into the authorization database Filter AST.
@Service
public final class EmbeddedLanguageFilterLowerer {

    private final EmbeddedLanguagePartialEvaluator partialEvaluator;

    /// Creates a query adapter backed by the generic partial evaluator.
    public EmbeddedLanguageFilterLowerer(EmbeddedLanguagePartialEvaluator partialEvaluator) {
        this.partialEvaluator = partialEvaluator;
    }

    /// Partially evaluates and lowers an object policy using known operation roots.
    public FilterAst partial(LanguageIr policy, Map<String, ?> roots) {
        PartialProgram partial = this.partialEvaluator.partial(policy, roots);
        if (partial.isConcrete()) {
            return new FilterAst(Boolean.TRUE.equals(partial.value()) ? FilterAst.all() : FilterAst.none());
        }
        try {
            if (!(partial.residual() instanceof LanguageIr.Expression residual)) {
                throw invalid("partial evaluation produced no residual expression");
            }
            return new FilterAst(this.filter(residual));
        } catch (AuthorizationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthorizationException("Invalid Object authorization policy: " + exception.getMessage());
        }
    }

    private FilterAst.Expression filter(LanguageIr.Expression expression) {
        return switch (expression) {
            case LanguageIr.Literal literal -> new FilterAst.Literal(literal.value());
            case LanguageIr.Reference reference -> objectField(reference);
            case LanguageIr.ListLiteral _ -> throw invalid("list expressions are not queryable");
            case LanguageIr.Unary unary when unary.operator() == LanguageIr.UnaryOperator.NOT -> new FilterAst.Unary(
                FilterAst.Operator.NOT,
                filter(unary.operand())
            );
            case LanguageIr.Unary unary when unary.operator() == LanguageIr.UnaryOperator.MINUS -> new FilterAst.Unary(
                FilterAst.Operator.NEGATE,
                filter(unary.operand())
            );
            case LanguageIr.Unary _ -> throw invalid("unary arithmetic is not queryable");
            case LanguageIr.Binary binary -> binary(binary);
            case LanguageIr.Conditional conditional -> FilterAst.or(
                FilterAst.and(filter(conditional.condition()), filter(conditional.whenTrue())),
                FilterAst.and(FilterAst.not(filter(conditional.condition())), filter(conditional.whenFalse()))
            );
        };
    }

    private FilterAst.Expression binary(LanguageIr.Binary expression) {
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

    private static FilterAst.Field objectField(LanguageIr.Reference reference) {
        if (!reference.root().equals("object") || reference.path().size() != 1) {
            throw invalid("only direct object fields are queryable");
        }
        return new FilterAst.Field(reference.path().getFirst());
    }

    private static AuthorizationException invalid(String message) {
        return new AuthorizationException("Invalid Object authorization policy: " + message);
    }
}
