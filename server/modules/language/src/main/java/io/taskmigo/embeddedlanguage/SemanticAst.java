package io.taskmigo.embeddedlanguage;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/// Represents the immutable typed Semantic AST of one compiled Embedded Language program.
@SuppressWarnings({ "checkstyle:NeedBraces", "checkstyle:DeclarationOrder" })
public record SemanticAst(
    Expression expression,
    String sourceFingerprint,
    String languageVersion,
    String schemaFingerprint,
    String compilerFingerprint,
    CompilationMode mode,
    String profileFingerprint
) {
    /// The current Embedded Language contract version.
    public static final String LANGUAGE_VERSION = "0.5.0";

    public SemanticAst {
        Objects.requireNonNull(expression);
        Objects.requireNonNull(sourceFingerprint);
        Objects.requireNonNull(languageVersion);
        Objects.requireNonNull(schemaFingerprint);
        Objects.requireNonNull(compilerFingerprint);
        Objects.requireNonNull(mode);
        Objects.requireNonNull(profileFingerprint);
    }

    /// Creates a metadata-free Semantic AST for focused tests.
    public SemanticAst(Expression expression) {
        this(expression, "", LANGUAGE_VERSION, "", "", CompilationMode.PROGRAM, "");
    }

    /// Creates an artifact with explicit compilation metadata.
    public SemanticAst(
        Expression expression,
        String sourceFingerprint,
        String languageVersion,
        String schemaFingerprint,
        String compilerFingerprint
    ) {
        this(
            expression,
            sourceFingerprint,
            languageVersion,
            schemaFingerprint,
            compilerFingerprint,
            CompilationMode.PROGRAM,
            ""
        );
    }

    /// Returns the statically determined program result type.
    public LanguageType resultType() {
        return this.expression.type();
    }

    /// Returns whether the statically determined program result may be null.
    public boolean resultNullable() {
        return this.expression.nullable();
    }

    /// Represents one typed semantic expression.
    public sealed interface Expression
        permits Literal, Reference, ListLiteral, Binary, Unary, Conditional, Quantifier, Length
    {
        /// Returns the static type.
        LanguageType type();
        /// Returns dependent schema roots.
        Set<String> dependencies();
        /// Returns the source span.
        LanguageDiagnostic.SourceSpan span();

        /// Returns whether this expression may evaluate to null.
        default boolean nullable() {
            return type() == LanguageType.Scalar.NULL;
        }
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

        @Override
        public boolean nullable() {
            return this.value == null;
        }
    }

    /// Represents a statically resolved schema path.
    public record Reference(
        String root,
        List<String> path,
        LanguageType type,
        boolean nullable,
        boolean symbolic,
        Set<String> dependencies,
        LanguageDiagnostic.SourceSpan span
    ) implements Expression {
        public Reference(String root, List<String> path) {
            this(root, path, LanguageType.Scalar.STRING, false, false, Set.of(root), UNKNOWN_SPAN);
        }

        public Reference(
            String root,
            List<String> path,
            LanguageType type,
            boolean nullable,
            boolean symbolic,
            LanguageDiagnostic.SourceSpan span
        ) {
            this(root, path, type, nullable, symbolic, Set.of(root), span);
        }

        public Reference {
            Objects.requireNonNull(root);
            path = List.copyOf(path);
            Objects.requireNonNull(type);
            dependencies = Set.copyOf(dependencies);
            Objects.requireNonNull(span);
        }

        @Override
        public boolean nullable() {
            return this.nullable;
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

    /// Represents conditional control flow after semantic analysis.
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
                compatibleResultType(whenTrue, whenFalse),
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

        @Override
        public boolean nullable() {
            return this.whenTrue.nullable() || this.whenFalse.nullable();
        }
    }

    /// Represents a bounded collection quantifier with one lexical element binding.
    public record Quantifier(
        QuantifierOperator operator,
        Expression collection,
        String elementName,
        Expression predicate,
        LanguageType type,
        Set<String> dependencies,
        LanguageDiagnostic.SourceSpan span
    ) implements Expression {
        public Quantifier {
            Objects.requireNonNull(operator);
            Objects.requireNonNull(collection);
            Objects.requireNonNull(elementName);
            if (elementName.isBlank()) throw new IllegalArgumentException("quantifier element name must not be blank");
            Objects.requireNonNull(predicate);
            Objects.requireNonNull(type);
            dependencies = Set.copyOf(dependencies);
            Objects.requireNonNull(span);
        }

        @Override
        public boolean nullable() {
            return false;
        }
    }

    /// Represents the bounded `len` intrinsic.
    public record Length(
        Expression operand,
        LanguageType type,
        Set<String> dependencies,
        LanguageDiagnostic.SourceSpan span
    ) implements Expression {
        public Length {
            Objects.requireNonNull(operand);
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

    /// Identifies the three supported collection quantifiers.
    public enum QuantifierOperator {
        ALL,
        ANY,
        NONE,
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

    private static LanguageType compatibleResultType(Expression left, Expression right) {
        if (left.type().equals(right.type())) return left.type();
        if (left.type() == LanguageType.Scalar.NULL) return right.type();
        if (right.type() == LanguageType.Scalar.NULL) return left.type();
        throw new IllegalArgumentException("conditional branches have incompatible result types");
    }

    private static Set<String> union(Expression... expressions) {
        HashSet<String> result = new HashSet<>();
        for (Expression expression : expressions) result.addAll(expression.dependencies());
        return Set.copyOf(result);
    }

    private static @Nullable Object immutableValue(@Nullable Object value) {
        return value instanceof List<?> list ? list.stream().map(SemanticAst::immutableValue).toList() : value;
    }
}
