package io.taskmigo.auth.authorization.condition;

import io.taskmigo.auth.authorization.statement.Scope;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.ast.CompoundExpression;
import org.springframework.expression.spel.ast.Literal;
import org.springframework.expression.spel.ast.Operator;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Service;

/// Compiles Statement conditions into a restricted, deterministic authorization AST using Spring SpEL parsing.
@Service
public class AuthorizationCompiler {

    private static final int MAX_SOURCE_LENGTH = 2000;
    private static final int MAX_DEPTH = 20;
    private static final int MAX_NODES = 100;
    private static final Set<String> ROOTS = Set.of("principal", "request", "object");
    private final SpelExpressionParser parser = new SpelExpressionParser();

    /// Compiles one legacy expression using the restricted SpEL syntax retained for the next authorization phase.
    ///
    /// SpEL is used only for parsing; the resulting SpEL tree is validated node-by-node and translated into the
    /// application AST without evaluating user input. Object references are rejected for request Statements because
    /// request evaluation has no object value to bind.
    ///
    /// @param source the expression source to compile
    /// @param scope the Statement scope that controls whether object references are valid
    /// @return the immutable authorization AST
    /// @throws AuthorizationException when a condition is outside the supported DSL
    public Expression compile(String source, Scope scope) {
        if (source.length() > MAX_SOURCE_LENGTH) throw invalid("expression is too long");
        if (parenthesisDepth(source) > MAX_DEPTH) throw invalid("expression nesting is too deep");
        Expression result;
        try {
            result = this.translate(((SpelExpression) this.parser.parseExpression(source)).getAST(), 0, new Counter());
        } catch (ParseException | ClassCastException exception) {
            throw invalid("expression cannot be parsed");
        }
        if (scope == Scope.REQUEST && containsObject(result)) throw invalid(
            "object references are only valid for object Statements"
        );
        return result;
    }

    /// Represents one compiled authorization expression node.
    public sealed interface Expression permits LiteralValue, Reference, Binary, Unary {}

    /// Represents a string, numeric, or boolean literal.
    public record LiteralValue(Object value) implements Expression {
        public LiteralValue {
            Objects.requireNonNull(value);
        }
    }

    /// Represents a supported `principal.*`, `request.*`, or `object.*` value.
    public record Reference(String root, List<String> path) implements Expression {}

    /// Represents a binary operation between two expressions.
    public record Binary(BinaryOperator operator, Expression left, Expression right) implements Expression {}

    /// Represents a unary operation applied to one expression.
    public record Unary(UnaryOperator operator, Expression operand) implements Expression {}

    /// Supported binary operators.
    public enum BinaryOperator {
        OR,
        AND,
        EQUAL,
        NOT_EQUAL,
        GREATER,
        GREATER_OR_EQUAL,
        LESS,
        LESS_OR_EQUAL,
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        MODULO,
    }

    /// Supported unary operators.
    public enum UnaryOperator {
        NOT,
        PLUS,
        MINUS,
    }

    private Expression translate(SpelNode node, int depth, Counter counter) {
        if (depth > MAX_DEPTH) throw invalid("expression nesting is too deep");
        counter.nodes++;
        if (counter.nodes > MAX_NODES) throw invalid("expression contains too many nodes");
        return switch (node) {
            case Literal literal -> AuthorizationCompiler.literal(literal);
            case CompoundExpression compound -> this.reference(compound);
            case Operator operator -> this.operator(operator, depth, counter);
            default -> throw invalid("unsupported expression node: " + node.getClass().getSimpleName());
        };
    }

    private static Expression literal(Literal literal) {
        Object value = literal.getLiteralValue().getValue();
        if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
            throw invalid("unsupported literal");
        }
        return new LiteralValue(value);
    }

    private Expression reference(CompoundExpression compound) {
        List<String> parts = new ArrayList<>();
        for (int index = 0; index < compound.getChildCount(); index++) {
            SpelNode child = compound.getChild(index);
            if (!(child instanceof PropertyOrFieldReference property) || property.isNullSafe()) {
                throw invalid("unsupported property reference");
            }

            parts.add(property.getName());
        }
        if (parts.size() < 2 || !ROOTS.contains(parts.getFirst())) throw invalid("unsupported reference root");
        return new Reference(parts.getFirst(), List.copyOf(parts.subList(1, parts.size())));
    }

    private Expression operator(Operator operator, int depth, Counter counter) {
        String name = operator.getOperatorName();
        if (operator.getChildCount() == 1) {
            UnaryOperator unary = switch (name) {
                case "!" -> UnaryOperator.NOT;
                case "+" -> UnaryOperator.PLUS;
                case "-" -> UnaryOperator.MINUS;
                default -> throw invalid("unsupported unary operator");
            };
            return new Unary(unary, this.translate(operator.getChild(0), depth + 1, counter));
        }
        if (operator.getChildCount() != 2) throw invalid("unsupported operator arity");
        BinaryOperator binary = switch (name) {
            case "or" -> BinaryOperator.OR;
            case "and" -> BinaryOperator.AND;
            case "==" -> BinaryOperator.EQUAL;
            case "!=" -> BinaryOperator.NOT_EQUAL;
            case ">" -> BinaryOperator.GREATER;
            case ">=" -> BinaryOperator.GREATER_OR_EQUAL;
            case "<" -> BinaryOperator.LESS;
            case "<=" -> BinaryOperator.LESS_OR_EQUAL;
            case "+" -> BinaryOperator.ADD;
            case "-" -> BinaryOperator.SUBTRACT;
            case "*" -> BinaryOperator.MULTIPLY;
            case "/" -> BinaryOperator.DIVIDE;
            case "%" -> BinaryOperator.MODULO;
            default -> throw invalid("unsupported operator: " + name);
        };
        return new Binary(
            binary,
            this.translate(operator.getLeftOperand(), depth + 1, counter),
            this.translate(operator.getRightOperand(), depth + 1, counter)
        );
    }

    private static boolean containsObject(Expression expression) {
        return switch (expression) {
            case Reference reference -> reference.root().equals("object");
            case LiteralValue ignored -> false;
            case Binary binary -> containsObject(binary.left()) || containsObject(binary.right());
            case Unary unary -> containsObject(unary.operand());
        };
    }

    private static AuthorizationException invalid(String message) {
        return new AuthorizationException("Invalid authorization condition: " + message);
    }

    private static int parenthesisDepth(String source) {
        int depth = 0;
        int maximum = 0;
        for (int index = 0; index < source.length(); index++) {
            char character = source.charAt(index);
            if (character == '(') {
                depth++;
                maximum = Math.max(maximum, depth);
            }
            if (character == ')') depth--;
        }
        return maximum;
    }

    private static final class Counter {

        private int nodes;
    }
}
