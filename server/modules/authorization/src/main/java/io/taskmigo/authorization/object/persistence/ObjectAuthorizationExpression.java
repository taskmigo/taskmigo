package io.taskmigo.authorization.object.persistence;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Persistence-neutral logical expression owned by Object Authorization.
public sealed interface ObjectAuthorizationExpression
    permits
        ObjectAuthorizationExpression.Literal,
        ObjectAuthorizationExpression.Reference,
        ObjectAuthorizationExpression.ListValue,
        ObjectAuthorizationExpression.Unary,
        ObjectAuthorizationExpression.Binary,
        ObjectAuthorizationExpression.Conditional,
        ObjectAuthorizationExpression.Quantifier,
        ObjectAuthorizationExpression.Length
{
    record Literal(@Nullable Object value) implements ObjectAuthorizationExpression {}

    record Reference(String root, List<String> path) implements ObjectAuthorizationExpression {
        public Reference {
            Objects.requireNonNull(root);
            path = List.copyOf(path);
        }
    }

    record ListValue(List<ObjectAuthorizationExpression> values) implements ObjectAuthorizationExpression {
        public ListValue {
            values = List.copyOf(values);
        }
    }

    record Unary(
        UnaryOperator operator,
        ObjectAuthorizationExpression operand
    ) implements ObjectAuthorizationExpression {
        public Unary {
            Objects.requireNonNull(operator);
            Objects.requireNonNull(operand);
        }
    }

    record Binary(
        BinaryOperator operator,
        ObjectAuthorizationExpression left,
        ObjectAuthorizationExpression right
    ) implements ObjectAuthorizationExpression {
        public Binary {
            Objects.requireNonNull(operator);
            Objects.requireNonNull(left);
            Objects.requireNonNull(right);
        }
    }

    record Conditional(
        ObjectAuthorizationExpression condition,
        ObjectAuthorizationExpression whenTrue,
        ObjectAuthorizationExpression whenFalse
    ) implements ObjectAuthorizationExpression {
        public Conditional {
            Objects.requireNonNull(condition);
            Objects.requireNonNull(whenTrue);
            Objects.requireNonNull(whenFalse);
        }
    }

    record Quantifier(
        QuantifierOperator operator,
        ObjectAuthorizationExpression collection,
        String elementName,
        ObjectAuthorizationExpression predicate
    ) implements ObjectAuthorizationExpression {
        public Quantifier {
            Objects.requireNonNull(operator);
            Objects.requireNonNull(collection);
            Objects.requireNonNull(elementName);
            Objects.requireNonNull(predicate);
        }
    }

    record Length(ObjectAuthorizationExpression operand) implements ObjectAuthorizationExpression {
        public Length {
            Objects.requireNonNull(operand);
        }
    }

    enum UnaryOperator {
        NOT,
        PLUS,
        MINUS,
    }

    enum BinaryOperator {
        OR,
        AND,
        EQUAL,
        NOT_EQUAL,
        GREATER,
        GREATER_OR_EQUAL,
        LESS,
        LESS_OR_EQUAL,
        IN,
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        MODULO,
    }

    enum QuantifierOperator {
        ALL,
        ANY,
        NONE,
    }
}
