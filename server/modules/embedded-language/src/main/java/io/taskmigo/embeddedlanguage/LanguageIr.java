package io.taskmigo.embeddedlanguage;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Represents immutable typed Embedded Language intermediate representation.
@SuppressWarnings({ "checkstyle:NeedBraces", "checkstyle:DeclarationOrder" })
public record LanguageIr(
    Expression expression,
    String sourceFingerprint,
    String languageVersion,
    String schemaFingerprint,
    String compilerFingerprint
) {
    /// The current Embedded Language contract version.
    public static final String LANGUAGE_VERSION = "0.2.0";

    public LanguageIr {
        Objects.requireNonNull(expression);
        Objects.requireNonNull(sourceFingerprint);
        Objects.requireNonNull(languageVersion);
        Objects.requireNonNull(schemaFingerprint);
        Objects.requireNonNull(compilerFingerprint);
    }

    /// Creates metadata-free IR for focused tests.
    public LanguageIr(Expression expression) {
        this(expression, "", LANGUAGE_VERSION, "", "");
    }

    /// Represents one typed expression.
    public sealed interface Expression permits Literal, Reference, ListLiteral, Binary, Unary, Conditional {
        /// Returns the static type.
        LanguageType type();
        /// Returns dependent schema roots.
        Set<String> dependencies();
        /// Returns the source span.
        LanguageDiagnostic.SourceSpan span();
    }

    /// Represents an immutable literal.
    public record Literal(
        @Nullable Object value,
        LanguageType type,
        Set<String> dependencies,
        LanguageDiagnostic.SourceSpan span
    ) implements Expression {
        public Literal(@Nullable Object value) {
            this(value, infer(value), Set.of(), UNKNOWN_SPAN);
        }

        public Literal(@Nullable Object value, LanguageType type, LanguageDiagnostic.SourceSpan span) {
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
        LanguageType type,
        boolean nullable,
        Set<String> dependencies,
        LanguageDiagnostic.SourceSpan span
    ) implements Expression {
        public Reference(String root, List<String> path) {
            this(root, path, LanguageType.Scalar.STRING, false, Set.of(root), UNKNOWN_SPAN);
        }

        public Reference(
            String root,
            List<String> path,
            LanguageType type,
            boolean nullable,
            LanguageDiagnostic.SourceSpan span
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
        LanguageType type,
        Set<String> dependencies,
        LanguageDiagnostic.SourceSpan span
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
        LanguageType type,
        Set<String> dependencies,
        LanguageDiagnostic.SourceSpan span
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
        LanguageType type,
        Set<String> dependencies,
        LanguageDiagnostic.SourceSpan span
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
        LanguageType type,
        Set<String> dependencies,
        LanguageDiagnostic.SourceSpan span
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

    private static final LanguageDiagnostic.SourceSpan UNKNOWN_SPAN = new LanguageDiagnostic.SourceSpan(1, 0, 1, 0);

    private static LanguageType infer(@Nullable Object value) {
        return switch (value) {
            case null -> LanguageType.Scalar.NULL;
            case Boolean _ -> LanguageType.Scalar.BOOL;
            case String _ -> LanguageType.Scalar.STRING;
            case Number _ -> LanguageType.Scalar.NUMBER;
            case List<?> list when list.isEmpty() -> new LanguageType.ListType(LanguageType.Scalar.NULL);
            case List<?> list -> new LanguageType.ListType(infer(list.getFirst()));
            default -> throw new IllegalArgumentException("unsupported program literal value");
        };
    }

    private static LanguageType infer(BinaryOperator operator) {
        return switch (operator) {
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> LanguageType.Scalar.NUMBER;
            default -> LanguageType.Scalar.BOOL;
        };
    }

    private static LanguageType infer(UnaryOperator operator) {
        return operator == UnaryOperator.NOT ? LanguageType.Scalar.BOOL : LanguageType.Scalar.NUMBER;
    }

    private static Set<String> union(Expression... expressions) {
        HashSet<String> result = new HashSet<>();
        for (Expression expression : expressions) result.addAll(expression.dependencies());
        return Set.copyOf(result);
    }

    private static @Nullable Object immutableValue(@Nullable Object value) {
        return value instanceof List<?> list ? list.stream().map(LanguageIr::immutableValue).toList() : value;
    }
}
