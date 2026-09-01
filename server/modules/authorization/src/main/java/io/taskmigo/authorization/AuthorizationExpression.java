package io.taskmigo.authorization;

import java.util.List;
import org.jspecify.annotations.Nullable;

public sealed interface AuthorizationExpression
    permits
        AuthorizationExpression.Literal,
        AuthorizationExpression.Reference,
        AuthorizationExpression.Unary,
        AuthorizationExpression.Binary
{
    record Literal(@Nullable Object value, ValueType type) implements AuthorizationExpression {}

    record Reference(Root root, List<String> path) implements AuthorizationExpression {
        public Reference {
            path = List.copyOf(path);
            if (path.isEmpty()) throw new IllegalArgumentException("Authorization reference requires a property path");
        }

        public String canonicalPath() {
            return root.name().toLowerCase(java.util.Locale.ROOT) + "." + String.join(".", path);
        }
    }

    record Unary(UnaryOperator operator, AuthorizationExpression operand) implements AuthorizationExpression {}

    record Binary(
        BinaryOperator operator,
        AuthorizationExpression left,
        AuthorizationExpression right
    ) implements AuthorizationExpression {}

    enum Root {
        PRINCIPAL,
        REQUEST,
        OBJECT,
    }

    enum UnaryOperator {
        NOT,
        NEGATE,
        POSITIVE,
    }

    enum BinaryOperator {
        EQ,
        NE,
        GT,
        GE,
        LT,
        LE,
        AND,
        OR,
        ADD,
        SUBTRACT,
        MULTIPLY,
        DIVIDE,
        MODULUS,
    }

    enum ValueType {
        NULL,
        BOOLEAN,
        NUMBER,
        STRING,
        UNKNOWN,
    }
}
