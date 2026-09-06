package io.taskmigo.embeddedlanguage;

import io.taskmigo.embeddedlanguage.antlr.EmbeddedLanguageBaseVisitor;
import io.taskmigo.embeddedlanguage.antlr.EmbeddedLanguageLexer;
import io.taskmigo.embeddedlanguage.antlr.EmbeddedLanguageParser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;

/// Compiles direct-body Embedded Language source into typed immutable IR.
@SuppressWarnings("checkstyle:NeedBraces")
public final class EmbeddedLanguageCompiler {

    private final CompilerLimits limits;

    /// Creates a compiler with the finite default contract limits.
    public EmbeddedLanguageCompiler() {
        this(CompilerLimits.defaults());
    }

    /// Creates a compiler with explicit finite limits.
    public EmbeddedLanguageCompiler(CompilerLimits limits) {
        this.limits = Objects.requireNonNull(limits);
    }

    /// Returns the identity of the compiler limits and language contract.
    public String contractFingerprint() {
        return this.limits.fingerprint() + ":" + LanguageIr.LANGUAGE_VERSION;
    }

    /// Compiles source against a consumer-owned environment schema.
    public LanguageIr compile(String source, EnvironmentSchema schema) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(schema);
        if (source.length() > this.limits.maxSourceCharacters()) {
            throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "program source exceeds the source-size limit",
                unknown()
            );
        }
        Errors errors = new Errors();
        EmbeddedLanguageLexer lexer = new EmbeddedLanguageLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        if (tokens.size() - 1 > this.limits.maxTokens()) {
            throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "program token count exceeds the token limit",
                unknown()
            );
        }
        EmbeddedLanguageParser parser = new EmbeddedLanguageParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        EmbeddedLanguageParser.ProgramContext tree = parser.program();
        if (!errors.diagnostics.isEmpty()) throw new EmbeddedLanguageException(errors.diagnostics);
        if (syntaxDepth(tree, 0) > limits.maxSyntaxDepth()) {
            throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "program syntax depth exceeds the limit",
                span(tree)
            );
        }
        if (tree.statement().isEmpty()) {
            throw failure(
                LanguageDiagnostic.Category.ControlFlowError,
                "program must return a boolean on every path",
                span(tree)
            );
        }
        Builder builder = new Builder(schema, this.limits);
        LanguageIr.Expression expression = builder.sequence(
            tree.statement(),
            new HashMap<>(),
            0,
            null,
            new HashSet<>()
        );
        if (expression.type() != LanguageType.Scalar.BOOL) {
            throw failure(LanguageDiagnostic.Category.TypeError, "program result must be Bool", expression.span());
        }
        return new LanguageIr(
            expression,
            fingerprint(source),
            LanguageIr.LANGUAGE_VERSION,
            schema.fingerprint(),
            limits.fingerprint()
        );
    }

    private static String fingerprint(String source) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static LanguageDiagnostic.SourceSpan span(ParseTree tree) {
        ParserRuleContext context = (ParserRuleContext) tree;
        Token start = context.getStart();
        Token stop = context.getStop();
        return new LanguageDiagnostic.SourceSpan(
            start.getLine(),
            start.getCharPositionInLine(),
            stop.getLine(),
            stop.getCharPositionInLine()
        );
    }

    private static LanguageDiagnostic.SourceSpan span(Token token) {
        return new LanguageDiagnostic.SourceSpan(
            token.getLine(),
            token.getCharPositionInLine(),
            token.getLine(),
            token.getCharPositionInLine() + token.getText().length()
        );
    }

    private static LanguageDiagnostic.SourceSpan unknown() {
        return new LanguageDiagnostic.SourceSpan(1, 0, 1, 0);
    }

    private static int syntaxDepth(ParseTree tree, int parentDepth) {
        int depth = parentDepth + 1;
        if (!(tree instanceof ParserRuleContext context) || context.children == null) return depth;
        int childDepth = depth;
        for (ParseTree child : context.children) childDepth = Math.max(childDepth, syntaxDepth(child, depth));
        return childDepth;
    }

    static EmbeddedLanguageException failure(
        LanguageDiagnostic.Category category,
        String message,
        LanguageDiagnostic.SourceSpan span
    ) {
        return new EmbeddedLanguageException(new LanguageDiagnostic(category, message, span));
    }

    private static final class Errors extends BaseErrorListener {

        private final List<LanguageDiagnostic> diagnostics = new ArrayList<>();

        @Override
        public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int column,
            String message,
            RecognitionException exception
        ) {
            this.diagnostics.add(
                new LanguageDiagnostic(
                    LanguageDiagnostic.Category.SyntaxError,
                    message,
                    new LanguageDiagnostic.SourceSpan(line, column, line, column + 1)
                )
            );
        }
    }

    private static final class Builder extends EmbeddedLanguageBaseVisitor<LanguageIr.Expression> {

        private final EnvironmentSchema schema;
        private final CompilerLimits limits;
        private int nodes;
        private final List<Map<String, LanguageIr.Expression>> scopes = new ArrayList<>();

        private Builder(EnvironmentSchema schema, CompilerLimits limits) {
            this.schema = schema;
            this.limits = limits;
        }

        private LanguageIr.Expression sequence(
            List<EmbeddedLanguageParser.StatementContext> statements,
            Map<String, LanguageIr.Expression> environment,
            int blockDepth,
            LanguageIr.@Nullable Expression continuation,
            Set<String> declared
        ) {
            if (blockDepth > limits.maxBlockDepth()) {
                throw failure(
                    LanguageDiagnostic.Category.ComplexityError,
                    "program block depth exceeds the limit",
                    unknown()
                );
            }
            if (statements.isEmpty()) {
                if (continuation == null) throw failure(
                    LanguageDiagnostic.Category.ControlFlowError,
                    "program must return a boolean on every path",
                    unknown()
                );
                return continuation;
            }
            EmbeddedLanguageParser.StatementContext statement = statements.getFirst();
            Map<String, LanguageIr.Expression> nextEnvironment = new HashMap<>(environment);
            scopes.add(nextEnvironment);
            try {
                if (statement.constDecl() != null) {
                    String name = statement.constDecl().IDENT().getText();
                    if (declared.contains(name)) throw failure(
                        LanguageDiagnostic.Category.BindingError,
                        "local binding is already declared: " + name,
                        span(statement)
                    );
                    LanguageIr.Expression value = expression(statement.constDecl().expression());
                    nextEnvironment.put(name, value);
                    declared.add(name);
                    return sequence(
                        statements.subList(1, statements.size()),
                        nextEnvironment,
                        blockDepth,
                        continuation,
                        declared
                    );
                }
                if (statement.returnStatement() != null) {
                    return expression(statement.returnStatement().expression());
                }

                EmbeddedLanguageParser.IfStatementContext conditional = statement.ifStatement();
                boolean hasElse = conditional.getChildCount() > 5;
                boolean hasFollowingStatements = statements.size() > 1;
                LanguageIr.Expression rest =
                    hasFollowingStatements || !hasElse
                        ? sequence(
                              statements.subList(1, statements.size()),
                              environment,
                              blockDepth,
                              continuation,
                              declared
                          )
                        : continuation;
                LanguageIr.Expression whenTrue = block(conditional.block(0), environment, blockDepth + 1, rest);
                LanguageIr.Expression whenFalse;
                if (hasElse) {
                    ParseTree elseTree = conditional.getChild(6);
                    whenFalse =
                        elseTree instanceof EmbeddedLanguageParser.BlockContext block
                            ? block(block, environment, blockDepth + 1, rest)
                            : elseTree instanceof EmbeddedLanguageParser.IfStatementContext nested
                              ? ifStatement(nested, environment, blockDepth + 1, rest)
                              : requireExpression(rest);
                } else {
                    whenFalse = requireExpression(rest);
                }
                LanguageIr.Expression condition = expression(conditional.expression());
                require(condition, LanguageType.Scalar.BOOL, "if condition must be Bool");
                requireMatchingBranches(whenTrue, whenFalse);
                return folded(node(new LanguageIr.Conditional(condition, whenTrue, whenFalse)));
            } finally {
                scopes.removeLast();
            }
        }

        private LanguageIr.Expression ifStatement(
            EmbeddedLanguageParser.IfStatementContext conditional,
            Map<String, LanguageIr.Expression> environment,
            int blockDepth,
            LanguageIr.@Nullable Expression continuation
        ) {
            LanguageIr.Expression rest = continuation;
            LanguageIr.Expression whenTrue = block(conditional.block(0), environment, blockDepth + 1, rest);
            LanguageIr.Expression whenFalse;
            if (conditional.getChildCount() > 5) {
                ParseTree elseTree = conditional.getChild(6);
                whenFalse =
                    elseTree instanceof EmbeddedLanguageParser.BlockContext block
                        ? block(block, environment, blockDepth + 1, rest)
                        : elseTree instanceof EmbeddedLanguageParser.IfStatementContext nested
                          ? ifStatement(nested, environment, blockDepth + 1, rest)
                          : requireExpression(rest);
            } else {
                whenFalse = requireExpression(rest);
            }
            LanguageIr.Expression condition = expression(conditional.expression());
            require(condition, LanguageType.Scalar.BOOL, "if condition must be Bool");
            requireMatchingBranches(whenTrue, whenFalse);
            return folded(node(new LanguageIr.Conditional(condition, whenTrue, whenFalse)));
        }

        private static void requireMatchingBranches(LanguageIr.Expression whenTrue, LanguageIr.Expression whenFalse) {
            if (!whenTrue.type().equals(whenFalse.type())) throw failure(
                LanguageDiagnostic.Category.TypeError,
                "if branches must return the same type",
                whenFalse.span()
            );
        }

        private LanguageIr.Expression block(
            EmbeddedLanguageParser.BlockContext block,
            Map<String, LanguageIr.Expression> environment,
            int blockDepth,
            LanguageIr.@Nullable Expression continuation
        ) {
            return sequence(block.statement(), new HashMap<>(environment), blockDepth, continuation, new HashSet<>());
        }

        private static LanguageIr.Expression requireExpression(LanguageIr.@Nullable Expression expression) {
            return Objects.requireNonNull(expression);
        }

        private LanguageIr.Expression expression(ParseTree tree) {
            LanguageIr.Expression result = visit(tree);
            if (result == null) throw failure(
                LanguageDiagnostic.Category.SyntaxError,
                "unsupported program expression",
                span(tree)
            );
            return result;
        }

        @Override
        public LanguageIr.Expression visitLiteral(EmbeddedLanguageParser.LiteralContext context) {
            Token token = context.getStart();
            Object value = switch (token.getType()) {
                case EmbeddedLanguageParser.TRUE -> true;
                case EmbeddedLanguageParser.FALSE -> false;
                case EmbeddedLanguageParser.NULL -> null;
                case EmbeddedLanguageParser.NUMBER -> parseNumber(token);
                case EmbeddedLanguageParser.STRING -> parseString(token);
                default -> throw failure(LanguageDiagnostic.Category.SyntaxError, "invalid literal", span(token));
            };
            return node(new LanguageIr.Literal(value, typeOf(value), Set.of(), span(context)));
        }

        @Override
        public LanguageIr.Expression visitReference(EmbeddedLanguageParser.ReferenceContext context) {
            String root = context.IDENT(0).getText();
            List<String> path = context.IDENT().stream().skip(1).map(TerminalNode::getText).toList();
            if (path.isEmpty()) {
                for (int index = scopes.size() - 1; index >= 0; index--) {
                    LanguageIr.Expression local = scopes.get(index).get(root);
                    if (local != null) return local;
                }
            }
            EnvironmentSchema.Field field = schema.resolve(root, path);
            if (field == null) throw failure(
                LanguageDiagnostic.Category.BindingError,
                "unknown program reference: " + context.getText(),
                span(context)
            );
            return node(
                new LanguageIr.Reference(root, path, field.type(), field.nullable(), Set.of(root), span(context))
            );
        }

        @Override
        public LanguageIr.Expression visitPrimary(EmbeddedLanguageParser.PrimaryContext context) {
            if (context.expression() != null) return expression(context.expression());
            if (context.literal() != null) return expression(context.literal());
            if (context.listLiteral() != null) return expression(context.listLiteral());
            return expression(context.reference());
        }

        @Override
        public LanguageIr.Expression visitListLiteral(EmbeddedLanguageParser.ListLiteralContext context) {
            if (context.expression().size() > limits.maxListElements()) throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "list literal exceeds the element limit",
                span(context)
            );
            List<LanguageIr.Expression> values = context.expression().stream().map(this::expression).toList();
            LanguageType element = values.isEmpty() ? LanguageType.Scalar.NULL : values.getFirst().type();
            for (LanguageIr.Expression value : values)
                if (!value.type().equals(element)) throw failure(
                    LanguageDiagnostic.Category.TypeError,
                    "list elements must have one homogeneous type",
                    value.span()
                );
            return node(
                new LanguageIr.ListLiteral(
                    values,
                    new LanguageType.ListType(element),
                    values
                        .stream()
                        .flatMap(value -> value.dependencies().stream())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    span(context)
                )
            );
        }

        @Override
        public LanguageIr.Expression visitUnaryExpression(EmbeddedLanguageParser.UnaryExpressionContext context) {
            if (context.primary() != null) return expression(context.primary());
            LanguageIr.Expression operand = expression(context.unaryExpression());
            LanguageIr.UnaryOperator operator = switch (context.getStart().getType()) {
                case EmbeddedLanguageParser.NOT -> LanguageIr.UnaryOperator.NOT;
                case EmbeddedLanguageParser.PLUS -> LanguageIr.UnaryOperator.PLUS;
                case EmbeddedLanguageParser.MINUS -> LanguageIr.UnaryOperator.MINUS;
                default -> throw failure(
                    LanguageDiagnostic.Category.SyntaxError,
                    "invalid unary operator",
                    span(context)
                );
            };
            require(
                operand,
                operator == LanguageIr.UnaryOperator.NOT ? LanguageType.Scalar.BOOL : LanguageType.Scalar.NUMBER,
                "unary operator has an incompatible operand"
            );
            return folded(
                node(
                    new LanguageIr.Unary(
                        operator,
                        operand,
                        operator == LanguageIr.UnaryOperator.NOT
                            ? LanguageType.Scalar.BOOL
                            : LanguageType.Scalar.NUMBER,
                        operand.dependencies(),
                        span(context)
                    )
                )
            );
        }

        @Override
        public LanguageIr.Expression visitMultiplicativeExpression(
            EmbeddedLanguageParser.MultiplicativeExpressionContext context
        ) {
            return chain(context.unaryExpression(), context);
        }

        @Override
        public LanguageIr.Expression visitAdditiveExpression(EmbeddedLanguageParser.AdditiveExpressionContext context) {
            return chain(context.multiplicativeExpression(), context);
        }

        @Override
        public LanguageIr.Expression visitComparisonExpression(
            EmbeddedLanguageParser.ComparisonExpressionContext context
        ) {
            return chain(context.membershipExpression(), context);
        }

        @Override
        public LanguageIr.Expression visitEqualityExpression(EmbeddedLanguageParser.EqualityExpressionContext context) {
            return chain(context.comparisonExpression(), context);
        }

        private LanguageIr.Expression chain(List<? extends ParseTree> operands, ParserRuleContext context) {
            LanguageIr.Expression result = expression(operands.getFirst());
            for (int index = 1; index < operands.size(); index++) {
                Token operator = ((TerminalNode) context.getChild(index * 2 - 1)).getSymbol();
                result = binary(operator, result, expression(operands.get(index)), span(context));
            }
            return result;
        }

        @Override
        public LanguageIr.Expression visitMembershipExpression(
            EmbeddedLanguageParser.MembershipExpressionContext context
        ) {
            LanguageIr.Expression left = expression(context.additiveExpression(0));
            return context.additiveExpression().size() == 1
                ? left
                : binary(context.IN().getSymbol(), left, expression(context.additiveExpression(1)), span(context));
        }

        @Override
        public LanguageIr.Expression visitAndExpression(EmbeddedLanguageParser.AndExpressionContext context) {
            return logical(context.equalityExpression(), LanguageIr.BinaryOperator.AND, context);
        }

        @Override
        public LanguageIr.Expression visitOrExpression(EmbeddedLanguageParser.OrExpressionContext context) {
            return logical(context.andExpression(), LanguageIr.BinaryOperator.OR, context);
        }

        private LanguageIr.Expression logical(
            List<? extends ParseTree> operands,
            LanguageIr.BinaryOperator operator,
            ParserRuleContext context
        ) {
            LanguageIr.Expression result = expression(operands.getFirst());
            for (int index = 1; index < operands.size(); index++) result = binary(
                operator,
                result,
                expression(operands.get(index)),
                span(context)
            );
            return result;
        }

        private LanguageIr.Expression binary(
            Token token,
            LanguageIr.Expression left,
            LanguageIr.Expression right,
            LanguageDiagnostic.SourceSpan sourceSpan
        ) {
            LanguageIr.BinaryOperator operator = switch (token.getType()) {
                case EmbeddedLanguageParser.OR -> LanguageIr.BinaryOperator.OR;
                case EmbeddedLanguageParser.AND -> LanguageIr.BinaryOperator.AND;
                case EmbeddedLanguageParser.EQUAL -> LanguageIr.BinaryOperator.EQUAL;
                case EmbeddedLanguageParser.NOT_EQUAL -> LanguageIr.BinaryOperator.NOT_EQUAL;
                case EmbeddedLanguageParser.LESS -> LanguageIr.BinaryOperator.LESS;
                case EmbeddedLanguageParser.LESS_EQUAL -> LanguageIr.BinaryOperator.LESS_OR_EQUAL;
                case EmbeddedLanguageParser.GREATER -> LanguageIr.BinaryOperator.GREATER;
                case EmbeddedLanguageParser.GREATER_EQUAL -> LanguageIr.BinaryOperator.GREATER_OR_EQUAL;
                case EmbeddedLanguageParser.IN -> LanguageIr.BinaryOperator.IN;
                case EmbeddedLanguageParser.PLUS -> LanguageIr.BinaryOperator.ADD;
                case EmbeddedLanguageParser.MINUS -> LanguageIr.BinaryOperator.SUBTRACT;
                case EmbeddedLanguageParser.STAR -> LanguageIr.BinaryOperator.MULTIPLY;
                case EmbeddedLanguageParser.SLASH -> LanguageIr.BinaryOperator.DIVIDE;
                case EmbeddedLanguageParser.PERCENT -> LanguageIr.BinaryOperator.MODULO;
                default -> throw failure(
                    LanguageDiagnostic.Category.SyntaxError,
                    "invalid binary operator",
                    sourceSpan
                );
            };
            return binary(operator, left, right, sourceSpan);
        }

        private LanguageIr.Expression binary(
            LanguageIr.BinaryOperator operator,
            LanguageIr.Expression left,
            LanguageIr.Expression right,
            LanguageDiagnostic.SourceSpan sourceSpan
        ) {
            LanguageType type = validate(operator, left, right, sourceSpan);
            return folded(node(new LanguageIr.Binary(operator, left, right, type, union(left, right), sourceSpan)));
        }

        private LanguageType validate(
            LanguageIr.BinaryOperator operator,
            LanguageIr.Expression left,
            LanguageIr.Expression right,
            LanguageDiagnostic.SourceSpan sourceSpan
        ) {
            if (operator == LanguageIr.BinaryOperator.AND || operator == LanguageIr.BinaryOperator.OR) {
                require(left, LanguageType.Scalar.BOOL, "boolean operators require Bool");
                require(right, LanguageType.Scalar.BOOL, "boolean operators require Bool");
                return LanguageType.Scalar.BOOL;
            }
            if (
                operator == LanguageIr.BinaryOperator.ADD ||
                operator == LanguageIr.BinaryOperator.SUBTRACT ||
                operator == LanguageIr.BinaryOperator.MULTIPLY ||
                operator == LanguageIr.BinaryOperator.DIVIDE ||
                operator == LanguageIr.BinaryOperator.MODULO
            ) {
                require(left, LanguageType.Scalar.NUMBER, "arithmetic requires Number");
                require(right, LanguageType.Scalar.NUMBER, "arithmetic requires Number");
                return LanguageType.Scalar.NUMBER;
            }
            if (operator == LanguageIr.BinaryOperator.IN) {
                if (
                    !(right.type() instanceof LanguageType.ListType list) || !left.type().equals(list.elementType())
                ) throw failure(
                    LanguageDiagnostic.Category.TypeError,
                    "in requires a value and a homogeneous List of that value type",
                    sourceSpan
                );
                return LanguageType.Scalar.BOOL;
            }
            if (
                operator == LanguageIr.BinaryOperator.GREATER ||
                operator == LanguageIr.BinaryOperator.GREATER_OR_EQUAL ||
                operator == LanguageIr.BinaryOperator.LESS ||
                operator == LanguageIr.BinaryOperator.LESS_OR_EQUAL
            ) {
                if (!left.type().equals(right.type()) || !left.type().ordered()) throw failure(
                    LanguageDiagnostic.Category.TypeError,
                    "ordered comparisons require matching String or Number values",
                    sourceSpan
                );
                return LanguageType.Scalar.BOOL;
            }
            if (
                left.type().equals(right.type()) ||
                (left.type() == LanguageType.Scalar.NULL && nullable(right)) ||
                (right.type() == LanguageType.Scalar.NULL && nullable(left))
            ) return LanguageType.Scalar.BOOL;
            throw failure(
                LanguageDiagnostic.Category.TypeError,
                "equality requires matching types or nullable values",
                sourceSpan
            );
        }

        private static boolean nullable(LanguageIr.Expression expression) {
            return expression instanceof LanguageIr.Reference reference && reference.nullable();
        }

        private LanguageIr.Expression folded(LanguageIr.Expression expression) {
            if (expression instanceof LanguageIr.Binary binary) {
                if (
                    binary.operator() == LanguageIr.BinaryOperator.AND &&
                    binary.left() instanceof LanguageIr.Literal literal &&
                    literal.value() instanceof Boolean value
                ) return value ? binary.right() : literal;
                if (
                    binary.operator() == LanguageIr.BinaryOperator.OR &&
                    binary.left() instanceof LanguageIr.Literal literal &&
                    literal.value() instanceof Boolean value
                ) return value ? literal : binary.right();
                if (
                    binary.left() instanceof LanguageIr.Literal left &&
                    binary.right() instanceof LanguageIr.Literal right
                ) {
                    try {
                        return literal(
                            EmbeddedLanguageEvaluator.compute(binary.operator(), left.value(), right.value()),
                            binary.span()
                        );
                    } catch (IllegalArgumentException exception) {
                        return expression;
                    }
                }
            }
            if (expression instanceof LanguageIr.Unary unary && unary.operand() instanceof LanguageIr.Literal literal) {
                try {
                    return literal(EmbeddedLanguageEvaluator.compute(unary.operator(), literal.value()), unary.span());
                } catch (IllegalArgumentException exception) {
                    return expression;
                }
            }
            if (
                expression instanceof LanguageIr.Conditional conditional &&
                conditional.condition() instanceof LanguageIr.Literal literal &&
                literal.value() instanceof Boolean value
            ) return value ? conditional.whenTrue() : conditional.whenFalse();
            return expression;
        }

        private LanguageIr.Expression literal(Object value, LanguageDiagnostic.SourceSpan sourceSpan) {
            return node(new LanguageIr.Literal(value, typeOf(value), Set.of(), sourceSpan));
        }

        private LanguageIr.Expression node(LanguageIr.Expression expression) {
            if (++nodes > limits.maxIrNodes()) throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "program IR node count exceeds the limit",
                expression.span()
            );
            return expression;
        }

        private static Set<String> union(LanguageIr.Expression left, LanguageIr.Expression right) {
            return java.util.stream.Stream.concat(left.dependencies().stream(), right.dependencies().stream()).collect(
                java.util.stream.Collectors.toUnmodifiableSet()
            );
        }

        private static void require(LanguageIr.Expression expression, LanguageType expected, String message) {
            if (!expression.type().equals(expected)) throw failure(
                LanguageDiagnostic.Category.TypeError,
                message,
                expression.span()
            );
        }

        private static LanguageType typeOf(@Nullable Object value) {
            return switch (value) {
                case null -> LanguageType.Scalar.NULL;
                case Boolean _ -> LanguageType.Scalar.BOOL;
                case String _ -> LanguageType.Scalar.STRING;
                case Number _ -> LanguageType.Scalar.NUMBER;
                default -> throw new IllegalArgumentException("unsupported Embedded Language value");
            };
        }

        private static BigDecimal parseNumber(Token token) {
            try {
                return new BigDecimal(token.getText());
            } catch (NumberFormatException exception) {
                throw failure(LanguageDiagnostic.Category.TypeError, "invalid finite Number literal", span(token));
            }
        }

        private static String parseString(Token token) {
            String text = token.getText();
            StringBuilder result = new StringBuilder();
            for (int index = 1; index < text.length() - 1; index++) {
                char value = text.charAt(index);
                if (value != '\\') result.append(value);
                else {
                    char escaped = text.charAt(++index);
                    result.append(
                        switch (escaped) {
                            case '"' -> '"';
                            case '\\' -> '\\';
                            case '/' -> '/';
                            case 'b' -> '\b';
                            case 'f' -> '\f';
                            case 'n' -> '\n';
                            case 'r' -> '\r';
                            case 't' -> '\t';
                            case 'u' -> (char) Integer.parseInt(text.substring(index + 1, (index += 4)), 16);
                            default -> throw failure(
                                LanguageDiagnostic.Category.SyntaxError,
                                "invalid string escape",
                                span(token)
                            );
                        }
                    );
                }
            }
            return result.toString();
        }
    }
}
