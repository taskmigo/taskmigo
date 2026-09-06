package io.taskmigo.policy;

import io.taskmigo.policy.antlr.PolicyLanguageBaseVisitor;
import io.taskmigo.policy.antlr.PolicyLanguageLexer;
import io.taskmigo.policy.antlr.PolicyLanguageParser;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
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

/// Compiles direct-body Policy Language source into typed immutable IR.
@SuppressWarnings("checkstyle:NeedBraces")
public final class PolicyCompiler {

    private final CompilerLimits limits;

    /// Creates a compiler with the finite default contract limits.
    public PolicyCompiler() {
        this(CompilerLimits.defaults());
    }

    /// Creates a compiler with explicit finite limits.
    public PolicyCompiler(CompilerLimits limits) {
        this.limits = Objects.requireNonNull(limits);
    }

    /// Returns the identity of the compiler limits and language contract.
    public String contractFingerprint() {
        return this.limits.fingerprint() + ":" + PolicyIr.LANGUAGE_VERSION;
    }

    /// Compiles source against a consumer-owned environment schema.
    public PolicyIr compile(String source, EnvironmentSchema schema) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(schema);
        if (source.length() > this.limits.maxSourceCharacters()) {
            throw failure(
                PolicyDiagnostic.Category.ComplexityError,
                "policy source exceeds the source-size limit",
                unknown()
            );
        }
        Errors errors = new Errors();
        PolicyLanguageLexer lexer = new PolicyLanguageLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
        if (tokens.size() - 1 > this.limits.maxTokens()) {
            throw failure(
                PolicyDiagnostic.Category.ComplexityError,
                "policy token count exceeds the token limit",
                unknown()
            );
        }
        PolicyLanguageParser parser = new PolicyLanguageParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        PolicyLanguageParser.PolicyContext tree = parser.policy();
        if (!errors.diagnostics.isEmpty()) throw new PolicyException(errors.diagnostics);
        if (syntaxDepth(tree, 0) > limits.maxSyntaxDepth()) {
            throw failure(
                PolicyDiagnostic.Category.ComplexityError,
                "policy syntax depth exceeds the limit",
                span(tree)
            );
        }
        if (tree.statement().isEmpty()) {
            throw failure(
                PolicyDiagnostic.Category.ControlFlowError,
                "policy must return a boolean on every path",
                span(tree)
            );
        }
        Builder builder = new Builder(schema, this.limits);
        PolicyIr.Expression expression = builder.sequence(tree.statement(), new HashMap<>(), 0, null);
        if (expression.type() != PolicyType.Scalar.BOOL) {
            throw failure(PolicyDiagnostic.Category.TypeError, "policy result must be Bool", expression.span());
        }
        return new PolicyIr(
            expression,
            fingerprint(source),
            PolicyIr.LANGUAGE_VERSION,
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

    private static PolicyDiagnostic.SourceSpan span(ParseTree tree) {
        ParserRuleContext context = (ParserRuleContext) tree;
        Token start = context.getStart();
        Token stop = context.getStop();
        return new PolicyDiagnostic.SourceSpan(
            start.getLine(),
            start.getCharPositionInLine(),
            stop.getLine(),
            stop.getCharPositionInLine()
        );
    }

    private static PolicyDiagnostic.SourceSpan span(Token token) {
        return new PolicyDiagnostic.SourceSpan(
            token.getLine(),
            token.getCharPositionInLine(),
            token.getLine(),
            token.getCharPositionInLine() + token.getText().length()
        );
    }

    private static PolicyDiagnostic.SourceSpan unknown() {
        return new PolicyDiagnostic.SourceSpan(1, 0, 1, 0);
    }

    private static int syntaxDepth(ParseTree tree, int parentDepth) {
        int depth = parentDepth + 1;
        if (!(tree instanceof ParserRuleContext context) || context.children == null) return depth;
        int childDepth = depth;
        for (ParseTree child : context.children) childDepth = Math.max(childDepth, syntaxDepth(child, depth));
        return childDepth;
    }

    static PolicyException failure(
        PolicyDiagnostic.Category category,
        String message,
        PolicyDiagnostic.SourceSpan span
    ) {
        return new PolicyException(new PolicyDiagnostic(category, message, span));
    }

    private static final class Errors extends BaseErrorListener {

        private final List<PolicyDiagnostic> diagnostics = new ArrayList<>();

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
                new PolicyDiagnostic(
                    PolicyDiagnostic.Category.SyntaxError,
                    message,
                    new PolicyDiagnostic.SourceSpan(line, column, line, column + 1)
                )
            );
        }
    }

    private static final class Builder extends PolicyLanguageBaseVisitor<PolicyIr.Expression> {

        private final EnvironmentSchema schema;
        private final CompilerLimits limits;
        private int nodes;
        private final List<Map<String, PolicyIr.Expression>> scopes = new ArrayList<>();

        private Builder(EnvironmentSchema schema, CompilerLimits limits) {
            this.schema = schema;
            this.limits = limits;
        }

        private PolicyIr.Expression sequence(
            List<PolicyLanguageParser.StatementContext> statements,
            Map<String, PolicyIr.Expression> environment,
            int blockDepth,
            PolicyIr.@Nullable Expression continuation
        ) {
            if (blockDepth > limits.maxBlockDepth()) {
                throw failure(
                    PolicyDiagnostic.Category.ComplexityError,
                    "policy block depth exceeds the limit",
                    unknown()
                );
            }
            if (statements.isEmpty()) {
                if (continuation == null) throw failure(
                    PolicyDiagnostic.Category.ControlFlowError,
                    "policy must return a boolean on every path",
                    unknown()
                );
                return continuation;
            }
            PolicyLanguageParser.StatementContext statement = statements.getFirst();
            Map<String, PolicyIr.Expression> nextEnvironment = new HashMap<>(environment);
            scopes.add(nextEnvironment);
            try {
                if (statement.constDecl() != null) {
                    String name = statement.constDecl().IDENT().getText();
                    if (environment.containsKey(name)) throw failure(
                        PolicyDiagnostic.Category.BindingError,
                        "local binding is already declared: " + name,
                        span(statement)
                    );
                    PolicyIr.Expression value = expression(statement.constDecl().expression());
                    nextEnvironment.put(name, value);
                    return sequence(
                        statements.subList(1, statements.size()),
                        nextEnvironment,
                        blockDepth,
                        continuation
                    );
                }
                if (statement.returnStatement() != null) {
                    return expression(statement.returnStatement().expression());
                }

                PolicyLanguageParser.IfStatementContext conditional = statement.ifStatement();
                boolean hasElse = conditional.getChildCount() > 5;
                boolean hasFollowingStatements = statements.size() > 1;
                PolicyIr.Expression rest =
                    hasFollowingStatements || !hasElse
                        ? sequence(statements.subList(1, statements.size()), environment, blockDepth, continuation)
                        : continuation;
                PolicyIr.Expression whenTrue = block(conditional.block(0), environment, blockDepth + 1, rest);
                PolicyIr.Expression whenFalse;
                if (hasElse) {
                    ParseTree elseTree = conditional.getChild(6);
                    whenFalse =
                        elseTree instanceof PolicyLanguageParser.BlockContext block
                            ? block(block, environment, blockDepth + 1, rest)
                            : elseTree instanceof PolicyLanguageParser.IfStatementContext nested
                              ? ifStatement(nested, environment, blockDepth + 1, rest)
                              : requireExpression(rest);
                } else {
                    whenFalse = requireExpression(rest);
                }
                PolicyIr.Expression condition = expression(conditional.expression());
                require(condition, PolicyType.Scalar.BOOL, "if condition must be Bool");
                return folded(node(new PolicyIr.Conditional(condition, whenTrue, whenFalse)));
            } finally {
                scopes.removeLast();
            }
        }

        private PolicyIr.Expression ifStatement(
            PolicyLanguageParser.IfStatementContext conditional,
            Map<String, PolicyIr.Expression> environment,
            int blockDepth,
            PolicyIr.@Nullable Expression continuation
        ) {
            PolicyIr.Expression rest = continuation;
            PolicyIr.Expression whenTrue = block(conditional.block(0), environment, blockDepth + 1, rest);
            PolicyIr.Expression whenFalse;
            if (conditional.getChildCount() > 5) {
                ParseTree elseTree = conditional.getChild(6);
                whenFalse =
                    elseTree instanceof PolicyLanguageParser.BlockContext block
                        ? block(block, environment, blockDepth + 1, rest)
                        : elseTree instanceof PolicyLanguageParser.IfStatementContext nested
                          ? ifStatement(nested, environment, blockDepth + 1, rest)
                          : requireExpression(rest);
            } else {
                whenFalse = requireExpression(rest);
            }
            PolicyIr.Expression condition = expression(conditional.expression());
            require(condition, PolicyType.Scalar.BOOL, "if condition must be Bool");
            return folded(node(new PolicyIr.Conditional(condition, whenTrue, whenFalse)));
        }

        private PolicyIr.Expression block(
            PolicyLanguageParser.BlockContext block,
            Map<String, PolicyIr.Expression> environment,
            int blockDepth,
            PolicyIr.@Nullable Expression continuation
        ) {
            return sequence(block.statement(), new HashMap<>(environment), blockDepth, continuation);
        }

        private static PolicyIr.Expression requireExpression(PolicyIr.@Nullable Expression expression) {
            return Objects.requireNonNull(expression);
        }

        private PolicyIr.Expression expression(ParseTree tree) {
            PolicyIr.Expression result = visit(tree);
            if (result == null) throw failure(
                PolicyDiagnostic.Category.SyntaxError,
                "unsupported policy expression",
                span(tree)
            );
            return result;
        }

        @Override
        public PolicyIr.Expression visitLiteral(PolicyLanguageParser.LiteralContext context) {
            Token token = context.getStart();
            Object value = switch (token.getType()) {
                case PolicyLanguageParser.TRUE -> true;
                case PolicyLanguageParser.FALSE -> false;
                case PolicyLanguageParser.NULL -> null;
                case PolicyLanguageParser.NUMBER -> parseNumber(token);
                case PolicyLanguageParser.STRING -> parseString(token);
                default -> throw failure(PolicyDiagnostic.Category.SyntaxError, "invalid literal", span(token));
            };
            return node(new PolicyIr.Literal(value, typeOf(value), Set.of(), span(context)));
        }

        @Override
        public PolicyIr.Expression visitReference(PolicyLanguageParser.ReferenceContext context) {
            String root = context.IDENT(0).getText();
            List<String> path = context.IDENT().stream().skip(1).map(TerminalNode::getText).toList();
            if (path.isEmpty()) {
                for (int index = scopes.size() - 1; index >= 0; index--) {
                    PolicyIr.Expression local = scopes.get(index).get(root);
                    if (local != null) return local;
                }
            }
            EnvironmentSchema.Field field = schema.resolve(root, path);
            if (field == null) throw failure(
                PolicyDiagnostic.Category.BindingError,
                "unknown policy reference: " + context.getText(),
                span(context)
            );
            return node(
                new PolicyIr.Reference(root, path, field.type(), field.nullable(), Set.of(root), span(context))
            );
        }

        @Override
        public PolicyIr.Expression visitPrimary(PolicyLanguageParser.PrimaryContext context) {
            if (context.expression() != null) return expression(context.expression());
            if (context.literal() != null) return expression(context.literal());
            if (context.listLiteral() != null) return expression(context.listLiteral());
            return expression(context.reference());
        }

        @Override
        public PolicyIr.Expression visitListLiteral(PolicyLanguageParser.ListLiteralContext context) {
            if (context.expression().size() > limits.maxListElements()) throw failure(
                PolicyDiagnostic.Category.ComplexityError,
                "list literal exceeds the element limit",
                span(context)
            );
            List<PolicyIr.Expression> values = context.expression().stream().map(this::expression).toList();
            PolicyType element = values.isEmpty() ? PolicyType.Scalar.NULL : values.getFirst().type();
            for (PolicyIr.Expression value : values)
                if (!value.type().equals(element)) throw failure(
                    PolicyDiagnostic.Category.TypeError,
                    "list elements must have one homogeneous type",
                    value.span()
                );
            return node(
                new PolicyIr.ListLiteral(
                    values,
                    new PolicyType.ListType(element),
                    values
                        .stream()
                        .flatMap(value -> value.dependencies().stream())
                        .collect(java.util.stream.Collectors.toUnmodifiableSet()),
                    span(context)
                )
            );
        }

        @Override
        public PolicyIr.Expression visitUnaryExpression(PolicyLanguageParser.UnaryExpressionContext context) {
            if (context.primary() != null) return expression(context.primary());
            PolicyIr.Expression operand = expression(context.unaryExpression());
            PolicyIr.UnaryOperator operator = switch (context.getStart().getType()) {
                case PolicyLanguageParser.NOT -> PolicyIr.UnaryOperator.NOT;
                case PolicyLanguageParser.PLUS -> PolicyIr.UnaryOperator.PLUS;
                case PolicyLanguageParser.MINUS -> PolicyIr.UnaryOperator.MINUS;
                default -> throw failure(
                    PolicyDiagnostic.Category.SyntaxError,
                    "invalid unary operator",
                    span(context)
                );
            };
            require(
                operand,
                operator == PolicyIr.UnaryOperator.NOT ? PolicyType.Scalar.BOOL : PolicyType.Scalar.NUMBER,
                "unary operator has an incompatible operand"
            );
            return folded(
                node(
                    new PolicyIr.Unary(
                        operator,
                        operand,
                        operator == PolicyIr.UnaryOperator.NOT ? PolicyType.Scalar.BOOL : PolicyType.Scalar.NUMBER,
                        operand.dependencies(),
                        span(context)
                    )
                )
            );
        }

        @Override
        public PolicyIr.Expression visitMultiplicativeExpression(
            PolicyLanguageParser.MultiplicativeExpressionContext context
        ) {
            return chain(context.unaryExpression(), context);
        }

        @Override
        public PolicyIr.Expression visitAdditiveExpression(PolicyLanguageParser.AdditiveExpressionContext context) {
            return chain(context.multiplicativeExpression(), context);
        }

        @Override
        public PolicyIr.Expression visitComparisonExpression(PolicyLanguageParser.ComparisonExpressionContext context) {
            return chain(context.membershipExpression(), context);
        }

        @Override
        public PolicyIr.Expression visitEqualityExpression(PolicyLanguageParser.EqualityExpressionContext context) {
            return chain(context.comparisonExpression(), context);
        }

        private PolicyIr.Expression chain(List<? extends ParseTree> operands, ParserRuleContext context) {
            PolicyIr.Expression result = expression(operands.getFirst());
            for (int index = 1; index < operands.size(); index++) {
                Token operator = ((TerminalNode) context.getChild(index * 2 - 1)).getSymbol();
                result = binary(operator, result, expression(operands.get(index)), span(context));
            }
            return result;
        }

        @Override
        public PolicyIr.Expression visitMembershipExpression(PolicyLanguageParser.MembershipExpressionContext context) {
            PolicyIr.Expression left = expression(context.additiveExpression(0));
            return context.additiveExpression().size() == 1
                ? left
                : binary(context.IN().getSymbol(), left, expression(context.additiveExpression(1)), span(context));
        }

        @Override
        public PolicyIr.Expression visitAndExpression(PolicyLanguageParser.AndExpressionContext context) {
            return logical(context.equalityExpression(), PolicyIr.BinaryOperator.AND, context);
        }

        @Override
        public PolicyIr.Expression visitOrExpression(PolicyLanguageParser.OrExpressionContext context) {
            return logical(context.andExpression(), PolicyIr.BinaryOperator.OR, context);
        }

        private PolicyIr.Expression logical(
            List<? extends ParseTree> operands,
            PolicyIr.BinaryOperator operator,
            ParserRuleContext context
        ) {
            PolicyIr.Expression result = expression(operands.getFirst());
            for (int index = 1; index < operands.size(); index++) result = binary(
                operator,
                result,
                expression(operands.get(index)),
                span(context)
            );
            return result;
        }

        private PolicyIr.Expression binary(
            Token token,
            PolicyIr.Expression left,
            PolicyIr.Expression right,
            PolicyDiagnostic.SourceSpan sourceSpan
        ) {
            PolicyIr.BinaryOperator operator = switch (token.getType()) {
                case PolicyLanguageParser.OR -> PolicyIr.BinaryOperator.OR;
                case PolicyLanguageParser.AND -> PolicyIr.BinaryOperator.AND;
                case PolicyLanguageParser.EQUAL -> PolicyIr.BinaryOperator.EQUAL;
                case PolicyLanguageParser.NOT_EQUAL -> PolicyIr.BinaryOperator.NOT_EQUAL;
                case PolicyLanguageParser.LESS -> PolicyIr.BinaryOperator.LESS;
                case PolicyLanguageParser.LESS_EQUAL -> PolicyIr.BinaryOperator.LESS_OR_EQUAL;
                case PolicyLanguageParser.GREATER -> PolicyIr.BinaryOperator.GREATER;
                case PolicyLanguageParser.GREATER_EQUAL -> PolicyIr.BinaryOperator.GREATER_OR_EQUAL;
                case PolicyLanguageParser.IN -> PolicyIr.BinaryOperator.IN;
                case PolicyLanguageParser.PLUS -> PolicyIr.BinaryOperator.ADD;
                case PolicyLanguageParser.MINUS -> PolicyIr.BinaryOperator.SUBTRACT;
                case PolicyLanguageParser.STAR -> PolicyIr.BinaryOperator.MULTIPLY;
                case PolicyLanguageParser.SLASH -> PolicyIr.BinaryOperator.DIVIDE;
                case PolicyLanguageParser.PERCENT -> PolicyIr.BinaryOperator.MODULO;
                default -> throw failure(PolicyDiagnostic.Category.SyntaxError, "invalid binary operator", sourceSpan);
            };
            return binary(operator, left, right, sourceSpan);
        }

        private PolicyIr.Expression binary(
            PolicyIr.BinaryOperator operator,
            PolicyIr.Expression left,
            PolicyIr.Expression right,
            PolicyDiagnostic.SourceSpan sourceSpan
        ) {
            PolicyType type = validate(operator, left, right, sourceSpan);
            return folded(node(new PolicyIr.Binary(operator, left, right, type, union(left, right), sourceSpan)));
        }

        private PolicyType validate(
            PolicyIr.BinaryOperator operator,
            PolicyIr.Expression left,
            PolicyIr.Expression right,
            PolicyDiagnostic.SourceSpan sourceSpan
        ) {
            if (operator == PolicyIr.BinaryOperator.AND || operator == PolicyIr.BinaryOperator.OR) {
                require(left, PolicyType.Scalar.BOOL, "boolean operators require Bool");
                require(right, PolicyType.Scalar.BOOL, "boolean operators require Bool");
                return PolicyType.Scalar.BOOL;
            }
            if (
                operator == PolicyIr.BinaryOperator.ADD ||
                operator == PolicyIr.BinaryOperator.SUBTRACT ||
                operator == PolicyIr.BinaryOperator.MULTIPLY ||
                operator == PolicyIr.BinaryOperator.DIVIDE ||
                operator == PolicyIr.BinaryOperator.MODULO
            ) {
                require(left, PolicyType.Scalar.NUMBER, "arithmetic requires Number");
                require(right, PolicyType.Scalar.NUMBER, "arithmetic requires Number");
                return PolicyType.Scalar.NUMBER;
            }
            if (operator == PolicyIr.BinaryOperator.IN) {
                if (
                    !(right.type() instanceof PolicyType.ListType list) || !left.type().equals(list.elementType())
                ) throw failure(
                    PolicyDiagnostic.Category.TypeError,
                    "in requires a value and a homogeneous List of that value type",
                    sourceSpan
                );
                return PolicyType.Scalar.BOOL;
            }
            if (
                operator == PolicyIr.BinaryOperator.GREATER ||
                operator == PolicyIr.BinaryOperator.GREATER_OR_EQUAL ||
                operator == PolicyIr.BinaryOperator.LESS ||
                operator == PolicyIr.BinaryOperator.LESS_OR_EQUAL
            ) {
                if (!left.type().equals(right.type()) || !left.type().ordered()) throw failure(
                    PolicyDiagnostic.Category.TypeError,
                    "ordered comparisons require matching String or Number values",
                    sourceSpan
                );
                return PolicyType.Scalar.BOOL;
            }
            if (
                left.type().equals(right.type()) ||
                (left.type() == PolicyType.Scalar.NULL && nullable(right)) ||
                (right.type() == PolicyType.Scalar.NULL && nullable(left))
            ) return PolicyType.Scalar.BOOL;
            throw failure(
                PolicyDiagnostic.Category.TypeError,
                "equality requires matching types or nullable values",
                sourceSpan
            );
        }

        private static boolean nullable(PolicyIr.Expression expression) {
            return expression instanceof PolicyIr.Reference reference && reference.nullable();
        }

        private PolicyIr.Expression folded(PolicyIr.Expression expression) {
            if (expression instanceof PolicyIr.Binary binary) {
                if (
                    binary.operator() == PolicyIr.BinaryOperator.AND &&
                    binary.left() instanceof PolicyIr.Literal literal &&
                    literal.value() instanceof Boolean value
                ) return value ? binary.right() : literal;
                if (
                    binary.operator() == PolicyIr.BinaryOperator.OR &&
                    binary.left() instanceof PolicyIr.Literal literal &&
                    literal.value() instanceof Boolean value
                ) return value ? literal : binary.right();
                if (
                    binary.left() instanceof PolicyIr.Literal left && binary.right() instanceof PolicyIr.Literal right
                ) {
                    try {
                        return literal(
                            PolicyEvaluator.compute(binary.operator(), left.value(), right.value()),
                            binary.span()
                        );
                    } catch (IllegalArgumentException exception) {
                        return expression;
                    }
                }
            }
            if (expression instanceof PolicyIr.Unary unary && unary.operand() instanceof PolicyIr.Literal literal) {
                try {
                    return literal(PolicyEvaluator.compute(unary.operator(), literal.value()), unary.span());
                } catch (IllegalArgumentException exception) {
                    return expression;
                }
            }
            if (
                expression instanceof PolicyIr.Conditional conditional &&
                conditional.condition() instanceof PolicyIr.Literal literal &&
                literal.value() instanceof Boolean value
            ) return value ? conditional.whenTrue() : conditional.whenFalse();
            return expression;
        }

        private PolicyIr.Expression literal(Object value, PolicyDiagnostic.SourceSpan sourceSpan) {
            return node(new PolicyIr.Literal(value, typeOf(value), Set.of(), sourceSpan));
        }

        private PolicyIr.Expression node(PolicyIr.Expression expression) {
            if (++nodes > limits.maxIrNodes()) throw failure(
                PolicyDiagnostic.Category.ComplexityError,
                "policy IR node count exceeds the limit",
                expression.span()
            );
            return expression;
        }

        private static Set<String> union(PolicyIr.Expression left, PolicyIr.Expression right) {
            return java.util.stream.Stream.concat(left.dependencies().stream(), right.dependencies().stream()).collect(
                java.util.stream.Collectors.toUnmodifiableSet()
            );
        }

        private static void require(PolicyIr.Expression expression, PolicyType expected, String message) {
            if (!expression.type().equals(expected)) throw failure(
                PolicyDiagnostic.Category.TypeError,
                message,
                expression.span()
            );
        }

        private static PolicyType typeOf(@Nullable Object value) {
            return switch (value) {
                case null -> PolicyType.Scalar.NULL;
                case Boolean _ -> PolicyType.Scalar.BOOL;
                case String _ -> PolicyType.Scalar.STRING;
                case Number _ -> PolicyType.Scalar.NUMBER;
                default -> throw new IllegalArgumentException("unsupported Policy Language value");
            };
        }

        private static BigDecimal parseNumber(Token token) {
            try {
                return new BigDecimal(token.getText());
            } catch (NumberFormatException exception) {
                throw failure(PolicyDiagnostic.Category.TypeError, "invalid finite Number literal", span(token));
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
                                PolicyDiagnostic.Category.SyntaxError,
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
