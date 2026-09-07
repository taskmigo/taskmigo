package io.taskmigo.auth.authorization.embeddedlanguage;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.filter.FilterAst;
import io.taskmigo.embeddedlanguage.EmbeddedLanguagePartialEvaluator;
import io.taskmigo.embeddedlanguage.LanguageType;
import io.taskmigo.embeddedlanguage.PartialProgram;
import io.taskmigo.embeddedlanguage.SemanticAst;
import java.util.Map;
import org.springframework.stereotype.Service;

/// Lowers generic Embedded Language residual Semantic AST into the authorization database Filter AST.
@Service
public final class EmbeddedLanguageFilterLowerer {

    private final EmbeddedLanguagePartialEvaluator partialEvaluator;

    /// Creates a query adapter backed by the generic partial evaluator.
    public EmbeddedLanguageFilterLowerer(EmbeddedLanguagePartialEvaluator partialEvaluator) {
        this.partialEvaluator = partialEvaluator;
    }

    /// Partially evaluates and lowers an object policy using known operation roots.
    public FilterAst partial(SemanticAst policy, Map<String, ?> roots) {
        try {
            PartialProgram partial = this.partialEvaluator.partial(policy, roots);
            return switch (partial) {
                case PartialProgram.Concrete concrete -> concrete(concrete);
                case PartialProgram.Residual residual -> residual(residual);
            };
        } catch (AuthorizationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AuthorizationException("Invalid Object authorization policy: " + exception.getMessage());
        }
    }

    private FilterAst concrete(PartialProgram.Concrete partial) {
        if (!(partial.value() instanceof Boolean value)) {
            throw invalid("Object authorization policy result is not Bool");
        }
        return new FilterAst(value ? FilterAst.all() : FilterAst.none());
    }

    private FilterAst residual(PartialProgram.Residual partial) {
        SemanticAst.Expression expression = partial.expression();
        if (!expression.type().equals(LanguageType.Scalar.BOOL)) {
            throw invalid("Object authorization policy residual result is not Bool");
        }
        return new FilterAst(this.filter(expression));
    }

    private FilterAst.Expression filter(SemanticAst.Expression expression) {
        return switch (expression) {
            case SemanticAst.Literal literal -> new FilterAst.Literal(literal.value());
            case SemanticAst.Reference reference -> objectField(reference);
            case SemanticAst.ListLiteral _ -> throw invalid("list expressions are not queryable");
            case SemanticAst.Unary unary when unary.operator() == SemanticAst.UnaryOperator.NOT -> new FilterAst.Unary(
                FilterAst.Operator.NOT,
                filter(unary.operand())
            );
            case SemanticAst.Unary unary when unary.operator() == SemanticAst.UnaryOperator.MINUS -> new FilterAst.Unary(
                FilterAst.Operator.NEGATE,
                filter(unary.operand())
            );
            case SemanticAst.Unary _ -> throw invalid("unary arithmetic is not queryable");
            case SemanticAst.Binary binary -> binary(binary);
            case SemanticAst.Conditional conditional -> FilterAst.or(
                FilterAst.and(filter(conditional.condition()), filter(conditional.whenTrue())),
                FilterAst.and(FilterAst.not(filter(conditional.condition())), filter(conditional.whenFalse()))
            );
        };
    }

    private FilterAst.Expression binary(SemanticAst.Binary expression) {
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

    private static FilterAst.Field objectField(SemanticAst.Reference reference) {
        if (!reference.root().equals("object") || reference.path().size() != 1) {
            throw invalid("only direct object fields are queryable");
        }
        return new FilterAst.Field(reference.path().getFirst());
    }

    private static AuthorizationException invalid(String message) {
        return new AuthorizationException("Invalid Object authorization policy: " + message);
    }
}
