package io.taskmigo.authorization;

import io.taskmigo.authorization.AuthorizationExpression.Binary;
import io.taskmigo.authorization.AuthorizationExpression.BinaryOperator;
import io.taskmigo.authorization.AuthorizationExpression.Literal;
import io.taskmigo.authorization.AuthorizationExpression.Reference;
import io.taskmigo.authorization.AuthorizationExpression.Root;
import io.taskmigo.authorization.AuthorizationExpression.Unary;
import io.taskmigo.authorization.AuthorizationExpression.UnaryOperator;
import io.taskmigo.authorization.AuthorizationExpression.ValueType;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.expression.spel.SpelNode;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.ast.CompoundExpression;
import org.springframework.expression.spel.ast.PropertyOrFieldReference;
import org.springframework.expression.spel.standard.SpelExpression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

final class AuthorizationExpressionCompiler {

    private static final int MAX_LENGTH = 1024;
    private static final int MAX_DEPTH = 32;
    private static final int MAX_NODES = 128;
    private final SpelExpressionParser parser = new SpelExpressionParser(new SpelParserConfiguration(false, false));

    AuthorizationExpression compile(String source, boolean objectAllowed) {
        if (source.length() > MAX_LENGTH) throw new IllegalArgumentException("when exceeds 1024 characters");
        SpelExpression expression;
        try {
            expression = (SpelExpression) this.parser.parseExpression(source);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid authorization condition: " + exception.getMessage());
        }
        SpelNode root = expression.getAST();
        Complexity complexity = complexity(root, 1);
        if (complexity.depth() > MAX_DEPTH || complexity.nodes() > MAX_NODES) {
            throw new IllegalArgumentException("Authorization condition exceeds AST complexity limits");
        }
        Typed typed = convert(root, objectAllowed);
        if (typed.type() != ValueType.BOOLEAN && typed.type() != ValueType.UNKNOWN) {
            throw new IllegalArgumentException("Authorization condition must evaluate to boolean");
        }
        return typed.expression();
    }

    private static Typed convert(SpelNode node, boolean objectAllowed) {
        if (node instanceof org.springframework.expression.spel.ast.Literal literal) {
            Object value = literal.getLiteralValue().getValue();
            return new Typed(new Literal(value, literalType(value)), literalType(value));
        }
        if (node instanceof CompoundExpression compound) return reference(compound, objectAllowed);
        return switch (node.getClass().getSimpleName()) {
            case "OperatorNot" -> unary(node, objectAllowed, UnaryOperator.NOT);
            case "OpPlus" -> node.getChildCount() == 1
                ? unary(node, objectAllowed, UnaryOperator.POSITIVE)
                : binary(node, objectAllowed, BinaryOperator.ADD);
            case "OpMinus" -> node.getChildCount() == 1
                ? unary(node, objectAllowed, UnaryOperator.NEGATE)
                : binary(node, objectAllowed, BinaryOperator.SUBTRACT);
            case "OpMultiply" -> binary(node, objectAllowed, BinaryOperator.MULTIPLY);
            case "OpDivide" -> binary(node, objectAllowed, BinaryOperator.DIVIDE);
            case "OpModulus" -> binary(node, objectAllowed, BinaryOperator.MODULUS);
            case "OpEQ" -> binary(node, objectAllowed, BinaryOperator.EQ);
            case "OpNE" -> binary(node, objectAllowed, BinaryOperator.NE);
            case "OpGT" -> binary(node, objectAllowed, BinaryOperator.GT);
            case "OpGE" -> binary(node, objectAllowed, BinaryOperator.GE);
            case "OpLT" -> binary(node, objectAllowed, BinaryOperator.LT);
            case "OpLE" -> binary(node, objectAllowed, BinaryOperator.LE);
            case "OpAnd" -> binary(node, objectAllowed, BinaryOperator.AND);
            case "OpOr" -> binary(node, objectAllowed, BinaryOperator.OR);
            default -> throw new IllegalArgumentException(
                "Unsupported SpEL construct " + node.getClass().getSimpleName() + ": " + node.toStringAST()
            );
        };
    }

    private static Typed reference(CompoundExpression compound, boolean objectAllowed) {
        if (compound.getChildCount() < 2) throw new IllegalArgumentException("Authorization references require a root and property");
        List<String> segments = new ArrayList<>(compound.getChildCount());
        for (int index = 0; index < compound.getChildCount(); index++) {
            SpelNode child = compound.getChild(index);
            if (!(child instanceof PropertyOrFieldReference property)) {
                throw new IllegalArgumentException("Only direct authorization property references are supported: " + compound.toStringAST());
            }
            segments.add(property.getName());
        }
        Root root;
        try {
            root = Root.valueOf(segments.removeFirst().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported authorization context root: " + compound.toStringAST());
        }
        if (root == Root.OBJECT && !objectAllowed) {
            throw new IllegalArgumentException("object.* references are only valid for target: object");
        }
        return new Typed(new Reference(root, segments), ValueType.UNKNOWN);
    }

    private static Typed unary(SpelNode node, boolean objectAllowed, UnaryOperator operator) {
        Typed operand = convert(node.getChild(0), objectAllowed);
        ValueType type = switch (operator) {
            case NOT -> {
                requireType(operand.type(), ValueType.BOOLEAN, "! requires a boolean operand");
                yield ValueType.BOOLEAN;
            }
            case NEGATE, POSITIVE -> {
                requireType(operand.type(), ValueType.NUMBER, "Unary numeric operators require a number");
                yield ValueType.NUMBER;
            }
        };
        return new Typed(new Unary(operator, operand.expression()), type);
    }

    private static Typed binary(SpelNode node, boolean objectAllowed, BinaryOperator operator) {
        if (node.getChildCount() != 2) throw new IllegalArgumentException("Invalid binary authorization expression");
        Typed left = convert(node.getChild(0), objectAllowed);
        Typed right = convert(node.getChild(1), objectAllowed);
        ValueType type = switch (operator) {
            case AND, OR -> {
                requireType(left.type(), ValueType.BOOLEAN, operator + " requires boolean operands");
                requireType(right.type(), ValueType.BOOLEAN, operator + " requires boolean operands");
                yield ValueType.BOOLEAN;
            }
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULUS -> {
                requireType(left.type(), ValueType.NUMBER, operator + " requires numeric operands");
                requireType(right.type(), ValueType.NUMBER, operator + " requires numeric operands");
                yield ValueType.NUMBER;
            }
            case EQ, NE -> {
                if (!equalityCompatible(left.type(), right.type())) {
                    throw new IllegalArgumentException("Incompatible equality operands");
                }
                yield ValueType.BOOLEAN;
            }
            case GT, GE, LT, LE -> {
                if (left.type() == ValueType.NULL || right.type() == ValueType.NULL) {
                    throw new IllegalArgumentException("Ordered comparison against null is not allowed");
                }
                if (!orderedCompatible(left.type(), right.type())) {
                    throw new IllegalArgumentException("Incompatible ordered comparison operands");
                }
                yield ValueType.BOOLEAN;
            }
        };
        return new Typed(new Binary(operator, left.expression(), right.expression()), type);
    }

    private static boolean equalityCompatible(ValueType left, ValueType right) {
        return left == ValueType.UNKNOWN || right == ValueType.UNKNOWN || left == ValueType.NULL || right == ValueType.NULL || left == right;
    }

    private static boolean orderedCompatible(ValueType left, ValueType right) {
        if (left == ValueType.UNKNOWN || right == ValueType.UNKNOWN) return true;
        return left == right && (left == ValueType.NUMBER || left == ValueType.STRING);
    }

    private static void requireType(ValueType actual, ValueType expected, String message) {
        if (actual != ValueType.UNKNOWN && actual != expected) throw new IllegalArgumentException(message);
    }

    private static ValueType literalType(Object value) {
        if (value == null) return ValueType.NULL;
        if (value instanceof Boolean) return ValueType.BOOLEAN;
        if (value instanceof Number) return ValueType.NUMBER;
        if (value instanceof String) return ValueType.STRING;
        throw new IllegalArgumentException("Unsupported authorization literal type: " + value.getClass().getSimpleName());
    }

    private static Complexity complexity(SpelNode node, int depth) {
        int nodes = 1;
        int maxDepth = depth;
        for (int index = 0; index < node.getChildCount(); index++) {
            Complexity child = complexity(node.getChild(index), depth + 1);
            nodes += child.nodes();
            maxDepth = Math.max(maxDepth, child.depth());
        }
        return new Complexity(nodes, maxDepth);
    }

    private record Typed(AuthorizationExpression expression, ValueType type) {}

    private record Complexity(int nodes, int depth) {}
}
