package io.taskmigo.query.persistence;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Persistence-neutral logical expression owned by the Query module.
public sealed interface QueryExpression
    permits
        QueryExpression.Literal,
        QueryExpression.Reference,
        QueryExpression.ListValue,
        QueryExpression.Unary,
        QueryExpression.Binary,
        QueryExpression.Conditional,
        QueryExpression.Quantifier,
        QueryExpression.Length
{
    record Literal(@Nullable Object value) implements QueryExpression {}

    record Reference(String root, List<String> path) implements QueryExpression {
        public Reference {
            Objects.requireNonNull(root);
            path = List.copyOf(path);
        }
    }

    record ListValue(List<QueryExpression> values) implements QueryExpression {
        public ListValue {
            values = List.copyOf(values);
        }
    }

    record Unary(UnaryOperator operator, QueryExpression operand) implements QueryExpression {
        public Unary {
            Objects.requireNonNull(operator);
            Objects.requireNonNull(operand);
        }
    }

    record Binary(BinaryOperator operator, QueryExpression left, QueryExpression right) implements QueryExpression {
        public Binary {
            Objects.requireNonNull(operator);
            Objects.requireNonNull(left);
            Objects.requireNonNull(right);
        }
    }

    record Conditional(
        QueryExpression condition,
        QueryExpression whenTrue,
        QueryExpression whenFalse
    ) implements QueryExpression {
        public Conditional {
            Objects.requireNonNull(condition);
            Objects.requireNonNull(whenTrue);
            Objects.requireNonNull(whenFalse);
        }
    }

    record Quantifier(
        QuantifierOperator operator,
        QueryExpression collection,
        String elementName,
        QueryExpression predicate
    ) implements QueryExpression {
        public Quantifier {
            Objects.requireNonNull(operator);
            Objects.requireNonNull(collection);
            Objects.requireNonNull(elementName);
            Objects.requireNonNull(predicate);
        }
    }

    record Length(QueryExpression operand) implements QueryExpression {
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
