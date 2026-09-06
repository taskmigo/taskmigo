package io.taskmigo.policy;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Represents immutable typed Policy Language intermediate representation.
@SuppressWarnings({ "checkstyle:NeedBraces", "checkstyle:DeclarationOrder" })
public record PolicyIr(
    Expression expression,
    String sourceFingerprint,
    String languageVersion,
    String schemaFingerprint,
    String compilerFingerprint
) {
    /// The current Policy Language contract version.
    public static final String LANGUAGE_VERSION = "0.1.0";

    public PolicyIr {
        Objects.requireNonNull(expression);
        Objects.requireNonNull(sourceFingerprint);
        Objects.requireNonNull(languageVersion);
        Objects.requireNonNull(schemaFingerprint);
        Objects.requireNonNull(compilerFingerprint);
    }

    /// Creates metadata-free IR for focused tests.
    public PolicyIr(Expression expression) {
        this(expression, "", LANGUAGE_VERSION, "", "");
    }

    /// Represents one typed expression.
    public sealed interface Expression permits Literal, Reference, ListLiteral, Binary, Unary, Conditional {
        /// Returns the static type.
        PolicyType type();
        /// Returns dependent schema roots.
        Set<String> dependencies();
        /// Returns the source span.
        PolicyDiagnostic.SourceSpan span();
    }

    /// Represents an immutable literal.
    public record Literal(
        @Nullable Object value,
        PolicyType type,
        Set<String> dependencies,
        PolicyDiagnostic.SourceSpan span
    ) implements Expression {
        public Literal(@Nullable Object value) {
            this(value, infer(value), Set.of(), UNKNOWN_SPAN);
        }

        public Literal(@Nullable Object value, PolicyType type, PolicyDiagnostic.SourceSpan span) {
            this(value, type, Set.of(), span);
        }

        public Literal {
            value = immutableValue(value);
            Objects.requireNonNull(type);
            dependencies = Set.copyOf(dependencies);
            Objects.requireNonNull(span);
        }
    }

    /// Represents a statically resolved schema path.
    public record Reference(
        String root,
        List<String> path,
        PolicyType type,
        boolean nullable,
        Set<String> dependencies,
        PolicyDiagnostic.SourceSpan span
    ) implements Expression {
        public Reference(String root, List<String> path) {
            this(root, path, PolicyType.Scalar.STRING, false, Set.of(root), UNKNOWN_SPAN);
        }

        public Reference(
            String root,
            List<String> path,
            PolicyType type,
            boolean nullable,
            PolicyDiagnostic.SourceSpan span
        ) {
            this(root, path, type, nullable, Set.of(root), span);
        }

        public Reference {
            Objects.requireNonNull(root);
            path = List.copyOf(path);
            Objects.requireNonNull(type);
            dependencies = Set.copyOf(dependencies);
            Objects.requireNonNull(span);
        }
    }

    /// Represents a homogeneous immutable list literal.
    public record ListLiteral(
        List<Expression> values,
        PolicyType type,
        Set<String> dependencies,
        PolicyDiagnostic.SourceSpan span
    ) implements Expression {
        public ListLiteral {
            values = List.copyOf(values);
            Objects.requireNonNull(type);
            dependencies = Set.copyOf(dependencies);
            Objects.requireNonNull(span);
        }
    }

    /// Represents a typed binary operation.
    public record Binary(
        BinaryOperator operator,
        Expression left,
        Expression right,
        PolicyType type,
        Set<String> dependencies,
        PolicyDiagnostic.SourceSpan span
    ) implements Expression {
        public Binary(BinaryOperator operator, Expression left, Expression right) {
            this(operator, left, right, infer(operator), union(left, right), left.span());
        }

        public Binary {
            Objects.requireNonNull(operator);
            Objects.requireNonNull(left);
            Objects.requireNonNull(right);
            Objects.requireNonNull(type);
            dependencies = Set.copyOf(dependencies);
            Objects.requireNonNull(span);
        }
    }

    /// Represents a typed unary operation.
    public record Unary(
        UnaryOperator operator,
        Expression operand,
        PolicyType type,
        Set<String> dependencies,
        PolicyDiagnostic.SourceSpan span
    ) implements Expression {
        public Unary(UnaryOperator operator, Expression operand) {
            this(operator, operand, infer(operator), operand.dependencies(), operand.span());
        }

        public Unary {
            Objects.requireNonNull(operator);
            Objects.requireNonNull(operand);
            Objects.requireNonNull(type);
            dependencies = Set.copyOf(dependencies);
            Objects.requireNonNull(span);
        }
    }

    /// Represents conditional control flow.
    public record Conditional(
        Expression condition,
        Expression whenTrue,
        Expression whenFalse,
        PolicyType type,
        Set<String> dependencies,
        PolicyDiagnostic.SourceSpan span
    ) implements Expression {
        public Conditional(Expression condition, Expression whenTrue, Expression whenFalse) {
            this(
                condition,
                whenTrue,
                whenFalse,
                whenTrue.type(),
                union(condition, whenTrue, whenFalse),
                condition.span()
            );
        }

        public Conditional {
            Objects.requireNonNull(condition);
            Objects.requireNonNull(whenTrue);
            Objects.requireNonNull(whenFalse);
            Objects.requireNonNull(type);
            dependencies = Set.copyOf(dependencies);
            Objects.requireNonNull(span);
        }
    }

    /// Supported binary operations.
    public enum BinaryOperator {
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

    /// Supported unary operations.
    public enum UnaryOperator {
        NOT,
        PLUS,
        MINUS,
    }

    private static final PolicyDiagnostic.SourceSpan UNKNOWN_SPAN = new PolicyDiagnostic.SourceSpan(1, 0, 1, 0);

    private static PolicyType infer(@Nullable Object value) {
        return switch (value) {
            case null -> PolicyType.Scalar.NULL;
            case Boolean _ -> PolicyType.Scalar.BOOL;
            case String _ -> PolicyType.Scalar.STRING;
            case Number _ -> PolicyType.Scalar.NUMBER;
            case List<?> list when list.isEmpty() -> new PolicyType.ListType(PolicyType.Scalar.NULL);
            case List<?> list -> new PolicyType.ListType(infer(list.getFirst()));
            default -> throw new IllegalArgumentException("unsupported policy literal value");
        };
    }

    private static PolicyType infer(BinaryOperator operator) {
        return switch (operator) {
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> PolicyType.Scalar.NUMBER;
            default -> PolicyType.Scalar.BOOL;
        };
    }

    private static PolicyType infer(UnaryOperator operator) {
        return operator == UnaryOperator.NOT ? PolicyType.Scalar.BOOL : PolicyType.Scalar.NUMBER;
    }

    private static Set<String> union(Expression... expressions) {
        HashSet<String> result = new HashSet<>();
        for (Expression expression : expressions) result.addAll(expression.dependencies());
        return Set.copyOf(result);
    }

    private static @Nullable Object immutableValue(@Nullable Object value) {
        return value instanceof List<?> list ? List.copyOf(list) : value;
    }
}
