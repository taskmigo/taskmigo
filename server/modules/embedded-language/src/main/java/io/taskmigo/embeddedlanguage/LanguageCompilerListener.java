package io.taskmigo.embeddedlanguage;

import io.taskmigo.embeddedlanguage.antlr.EmbeddedLanguageBaseListener;
import io.taskmigo.embeddedlanguage.antlr.EmbeddedLanguageParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;

/// Converts generated ANTLR parser events into typed language-owned IR without retaining a parse tree.
@SuppressWarnings({
    "checkstyle:NeedBraces",
    "checkstyle:OverloadMethodsDeclarationOrder",
    "checkstyle:UnnecessaryFullyQualifiedType",
})
final class LanguageCompilerListener extends EmbeddedLanguageBaseListener {

    private final EnvironmentSchema schema;
    private final CompilerLimits limits;
    private final BooleanSupplier invalidSyntax;
    private final List<Scope> scopes = new ArrayList<>();
    private Object[] values = new Object[64];
    private int[] marks = new int[64];
    private int valueCount;
    private int markCount;
    private @Nullable Program program;
    private int nodes;
    private int unaryDepth;

    LanguageCompilerListener(EnvironmentSchema schema, CompilerLimits limits, BooleanSupplier invalidSyntax) {
        this.schema = schema;
        this.limits = limits;
        this.invalidSyntax = invalidSyntax;
    }

    LanguageIr.Expression compile() {
        Program result = Objects.requireNonNull(this.program);
        return sequence(result.statements(), new Scope(null), 0, null, new HashSet<>());
    }

    @Override
    public void enterEveryRule(ParserRuleContext context) {
        if (this.markCount == this.marks.length) this.marks = Arrays.copyOf(this.marks, this.marks.length * 2);
        this.marks[this.markCount++] = this.valueCount;
    }

    @Override
    public void exitEveryRule(ParserRuleContext context) {
        int start = this.marks[--this.markCount];
        if (this.invalidSyntax.getAsBoolean()) {
            clearTo(start);
            return;
        }
        SyntaxNode value = build(context, start, this.valueCount);
        clearTo(start);
        if (this.markCount == 0) this.program = (Program) value;
        else push(value);
    }

    @Override
    public void visitTerminal(TerminalNode node) {
        push(node.getSymbol());
    }

    @Override
    public void visitErrorNode(ErrorNode node) {
        push(node.getSymbol());
    }

    @Override
    public void enterUnaryExpression(EmbeddedLanguageParser.UnaryExpressionContext context) {
        if (++this.unaryDepth > this.limits.maxSyntaxDepth()) {
            throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "program syntax depth exceeds the limit",
                span(context)
            );
        }
    }

    @Override
    public void exitUnaryExpression(EmbeddedLanguageParser.UnaryExpressionContext context) {
        this.unaryDepth--;
    }

    private void push(Object value) {
        if (this.valueCount == this.values.length) this.values = Arrays.copyOf(this.values, this.values.length * 2);
        this.values[this.valueCount++] = value;
    }

    private void clearTo(int size) {
        Arrays.fill(this.values, size, this.valueCount, null);
        this.valueCount = size;
    }

    private SyntaxNode build(ParserRuleContext context, int start, int end) {
        return switch (context.getRuleIndex()) {
            case EmbeddedLanguageParser.RULE_program -> new Program(statements(start, end));
            case EmbeddedLanguageParser.RULE_statement -> first(start, end, Statement.class);
            case EmbeddedLanguageParser.RULE_block -> new Block(statements(start, end));
            case EmbeddedLanguageParser.RULE_constDecl -> new Constant(
                token(start, end, EmbeddedLanguageParser.IDENT).getText(),
                first(start, end, Expression.class),
                span(context)
            );
            case EmbeddedLanguageParser.RULE_returnStatement -> new Returning(
                first(start, end, Expression.class),
                span(context)
            );
            case EmbeddedLanguageParser.RULE_ifStatement -> conditional(context, start, end);
            case EmbeddedLanguageParser.RULE_expression, EmbeddedLanguageParser.RULE_primary -> first(
                start,
                end,
                Expression.class
            );
            case EmbeddedLanguageParser.RULE_orExpression -> chain(
                start,
                end,
                token -> LanguageIr.BinaryOperator.OR
            );
            case EmbeddedLanguageParser.RULE_andExpression -> chain(
                start,
                end,
                token -> LanguageIr.BinaryOperator.AND
            );
            case EmbeddedLanguageParser.RULE_equalityExpression -> chain(
                start,
                end,
                token ->
                    switch (token.getType()) {
                        case EmbeddedLanguageParser.EQUAL -> LanguageIr.BinaryOperator.EQUAL;
                        case EmbeddedLanguageParser.NOT_EQUAL -> LanguageIr.BinaryOperator.NOT_EQUAL;
                        default -> throw new IllegalStateException("unsupported equality operator");
                    }
            );
            case EmbeddedLanguageParser.RULE_comparisonExpression -> chain(
                start,
                end,
                token ->
                    switch (token.getType()) {
                        case EmbeddedLanguageParser.LESS -> LanguageIr.BinaryOperator.LESS;
                        case EmbeddedLanguageParser.LESS_EQUAL -> LanguageIr.BinaryOperator.LESS_OR_EQUAL;
                        case EmbeddedLanguageParser.GREATER -> LanguageIr.BinaryOperator.GREATER;
                        case EmbeddedLanguageParser.GREATER_EQUAL -> LanguageIr.BinaryOperator.GREATER_OR_EQUAL;
                        default -> throw new IllegalStateException("unsupported comparison operator");
                    }
            );
            case EmbeddedLanguageParser.RULE_membershipExpression -> chain(
                start,
                end,
                token -> LanguageIr.BinaryOperator.IN
            );
            case EmbeddedLanguageParser.RULE_additiveExpression -> chain(
                start,
                end,
                token ->
                    switch (token.getType()) {
                        case EmbeddedLanguageParser.PLUS -> LanguageIr.BinaryOperator.ADD;
                        case EmbeddedLanguageParser.MINUS -> LanguageIr.BinaryOperator.SUBTRACT;
                        default -> throw new IllegalStateException("unsupported additive operator");
                    }
            );
            case EmbeddedLanguageParser.RULE_multiplicativeExpression -> chain(
                start,
                end,
                token ->
                    switch (token.getType()) {
                        case EmbeddedLanguageParser.STAR -> LanguageIr.BinaryOperator.MULTIPLY;
                        case EmbeddedLanguageParser.SLASH -> LanguageIr.BinaryOperator.DIVIDE;
                        case EmbeddedLanguageParser.PERCENT -> LanguageIr.BinaryOperator.MODULO;
                        default -> throw new IllegalStateException("unsupported multiplicative operator");
                    }
            );
            case EmbeddedLanguageParser.RULE_unaryExpression -> unary(context, start, end);
            case EmbeddedLanguageParser.RULE_reference -> reference(context, start, end);
            case EmbeddedLanguageParser.RULE_listLiteral -> new ListExpression(expressions(start, end), span(context));
            case EmbeddedLanguageParser.RULE_literal -> new Literal(first(start, end, Token.class));
            default -> throw new IllegalStateException("unsupported Embedded Language grammar rule");
        };
    }

    private Conditional conditional(ParserRuleContext context, int start, int end) {
        Expression condition = first(start, end, Expression.class);
        Block whenTrue = null;
        @Nullable
        List<Statement> whenFalse = null;
        boolean afterTrue = false;
        for (int index = start; index < end; index++) {
            Object item = this.values[index];
            if (item == condition) continue;
            if (!afterTrue && item instanceof Block block) {
                whenTrue = block;
                afterTrue = true;
                continue;
            }
            if (afterTrue) {
                if (item instanceof Block block) whenFalse = block.statements();
                else if (item instanceof Statement statement) whenFalse = List.of(statement);
            }
        }
        Block trueBlock = Objects.requireNonNull(whenTrue);
        return new Conditional(condition, trueBlock.statements(), whenFalse, span(context));
    }

    private Reference reference(ParserRuleContext context, int start, int end) {
        List<String> names = new ArrayList<>();
        for (int index = start; index < end; index++) {
            Object item = this.values[index];
            if (item instanceof Token token && token.getType() == EmbeddedLanguageParser.IDENT) names.add(token.getText());
        }
        return new Reference(names.getFirst(), names.subList(1, names.size()), span(context));
    }

    private Expression unary(ParserRuleContext context, int start, int end) {
        Expression operand = first(start, end, Expression.class);
        Token operator = firstOrNull(start, end, Token.class);
        if (operator == null || operator.getType() == Token.EOF) return operand;
        LanguageIr.UnaryOperator mapped = switch (operator.getType()) {
            case EmbeddedLanguageParser.NOT -> LanguageIr.UnaryOperator.NOT;
            case EmbeddedLanguageParser.PLUS -> LanguageIr.UnaryOperator.PLUS;
            case EmbeddedLanguageParser.MINUS -> LanguageIr.UnaryOperator.MINUS;
            default -> null;
        };
        return mapped == null ? operand : new Unary(mapped, operand, span(context));
    }

    private Expression chain(
        int start,
        int end,
        java.util.function.Function<Token, LanguageIr.BinaryOperator> op
    ) {
        Expression result = null;
        Token operator = null;
        for (int index = start; index < end; index++) {
            Object item = this.values[index];
            if (item instanceof Token token) {
                if (token.getType() != Token.EOF) operator = token;
                continue;
            }
            if (!(item instanceof Expression expression)) continue;
            if (result == null) {
                result = expression;
            } else {
                result = new Binary(
                    op.apply(Objects.requireNonNull(operator)),
                    result,
                    expression,
                    sourceSpan(result.span(), expression.span())
                );
                operator = null;
            }
        }
        return Objects.requireNonNull(result);
    }

    private List<Statement> statements(int start, int end) {
        List<Statement> result = new ArrayList<>();
        for (int index = start; index < end; index++) {
            Object item = this.values[index];
            if (item instanceof Statement statement) result.add(statement);
        }
        return List.copyOf(result);
    }

    private List<Expression> expressions(int start, int end) {
        List<Expression> result = new ArrayList<>();
        for (int index = start; index < end; index++) {
            Object item = this.values[index];
            if (item instanceof Expression expression) result.add(expression);
        }
        return List.copyOf(result);
    }

    private Token token(int start, int end, int type) {
        for (int index = start; index < end; index++) {
            Object item = this.values[index];
            if (item instanceof Token token && token.getType() == type) return token;
        }
        throw new IllegalStateException("expected parser token");
    }

    private <T> T first(int start, int end, Class<T> type) {
        T value = firstOrNull(start, end, type);
        if (value == null) throw new IllegalStateException("expected parser value");
        return value;
    }

    private <T> @Nullable T firstOrNull(int start, int end, Class<T> type) {
        for (int index = start; index < end; index++) {
            Object item = this.values[index];
            if (type.isInstance(item)) return type.cast(item);
        }
        return null;
    }

    private LanguageIr.Expression sequence(
        List<Statement> statements,
        Scope environment,
        int blockDepth,
        LanguageIr.@Nullable Expression continuation,
        Set<String> declared
    ) {
        if (blockDepth > limits.maxBlockDepth()) throw failure(
            LanguageDiagnostic.Category.ComplexityError,
            "program block depth exceeds the limit",
            unknown()
        );
        scopes.add(environment);
        try {
            for (int index = 0; index < statements.size(); index++) {
                Statement statement = statements.get(index);
                if (statement instanceof Constant constant) {
                    if (!declared.add(constant.name())) throw failure(
                        LanguageDiagnostic.Category.BindingError,
                        "local binding is already declared: " + constant.name(),
                        constant.span()
                    );
                    environment.put(constant.name(), lower(constant.value()));
                    continue;
                }
                if (statement instanceof Returning returning) return lower(returning.value());
                Conditional conditional = (Conditional) statement;
                boolean hasElse = conditional.whenFalse() != null;
                boolean hasFollowing = index + 1 < statements.size();
                LanguageIr.Expression rest =
                    hasFollowing || !hasElse
                        ? sequence(
                              statements.subList(index + 1, statements.size()),
                              new Scope(environment),
                              blockDepth,
                              continuation,
                              new HashSet<>(declared)
                          )
                        : continuation;
                LanguageIr.Expression whenTrue = sequence(
                    conditional.whenTrue(),
                    new Scope(environment),
                    blockDepth + 1,
                    rest,
                    new HashSet<>()
                );
                LanguageIr.Expression whenFalse = hasElse
                    ? lowerElse(Objects.requireNonNull(conditional.whenFalse()), environment, blockDepth + 1, rest)
                    : requireExpression(rest);
                LanguageIr.Expression condition = lower(conditional.condition());
                require(condition, LanguageType.Scalar.BOOL, "if condition must be Bool");
                requireMatchingBranches(whenTrue, whenFalse);
                return folded(node(new LanguageIr.Conditional(condition, whenTrue, whenFalse)));
            }
            if (continuation == null) throw failure(
                LanguageDiagnostic.Category.ControlFlowError,
                "program must return a boolean on every path",
                unknown()
            );
            return continuation;
        } finally {
            scopes.removeLast();
        }
    }

    private LanguageIr.Expression lowerElse(
        List<Statement> statements,
        Scope environment,
        int blockDepth,
        LanguageIr.@Nullable Expression continuation
    ) {
        if (statements.size() == 1 && statements.getFirst() instanceof Conditional conditional) {
            return lowerConditional(conditional, environment, blockDepth, continuation);
        }
        return sequence(statements, new Scope(environment), blockDepth, continuation, new HashSet<>());
    }

    private LanguageIr.Expression lowerConditional(
        Conditional conditional,
        Scope environment,
        int blockDepth,
        LanguageIr.@Nullable Expression continuation
    ) {
        LanguageIr.Expression whenTrue = sequence(
            conditional.whenTrue(),
            new Scope(environment),
            blockDepth + 1,
            continuation,
            new HashSet<>()
        );
        LanguageIr.Expression whenFalse =
            conditional.whenFalse() == null
                ? requireExpression(continuation)
                : lowerElse(Objects.requireNonNull(conditional.whenFalse()), environment, blockDepth + 1, continuation);
        LanguageIr.Expression condition = lower(conditional.condition());
        require(condition, LanguageType.Scalar.BOOL, "if condition must be Bool");
        requireMatchingBranches(whenTrue, whenFalse);
        return folded(node(new LanguageIr.Conditional(condition, whenTrue, whenFalse)));
    }

    private LanguageIr.Expression lower(Expression expression) {
        return switch (expression) {
            case Literal literal -> literal(literal.token());
            case Reference reference -> reference(reference);
            case ListExpression list -> list(list);
            case Unary unary -> unary(unary);
            case Binary binary -> binary(binary);
        };
    }

    private LanguageIr.Expression literal(Token token) {
        Object value = switch (token.getType()) {
            case EmbeddedLanguageParser.TRUE -> true;
            case EmbeddedLanguageParser.FALSE -> false;
            case EmbeddedLanguageParser.NULL -> null;
            case EmbeddedLanguageParser.NUMBER -> parseNumber(token);
            case EmbeddedLanguageParser.STRING -> parseString(token);
            default -> throw syntax("invalid literal", token);
        };
        return node(new LanguageIr.Literal(value, typeOf(value), Set.of(), span(token)));
    }

    private LanguageIr.Expression reference(Reference reference) {
        if (reference.path().isEmpty()) {
            LanguageIr.@Nullable Expression local = scopes.getLast().lookup(reference.root());
            if (local != null) return local;
        }
        EnvironmentSchema.Field field = schema.resolve(reference.root(), reference.path());
        if (field == null) throw failure(
            LanguageDiagnostic.Category.BindingError,
            "unknown program reference: " + reference.root() + String.join(".", reference.path()),
            reference.span()
        );
        return node(
            new LanguageIr.Reference(
                reference.root(),
                reference.path(),
                field.type(),
                field.nullable(),
                Set.of(reference.root()),
                reference.span()
            )
        );
    }

    private LanguageIr.Expression list(ListExpression expression) {
        if (expression.values().size() > limits.maxListElements()) throw failure(
            LanguageDiagnostic.Category.ComplexityError,
            "list literal exceeds the element limit",
            expression.span()
        );
        List<LanguageIr.Expression> values = new ArrayList<>(expression.values().size());
        Set<String> dependencies = new HashSet<>();
        for (Expression value : expression.values()) {
            LanguageIr.Expression lowered = lower(value);
            values.add(lowered);
            dependencies.addAll(lowered.dependencies());
        }
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
                Set.copyOf(dependencies),
                expression.span()
            )
        );
    }

    private LanguageIr.Expression unary(Unary expression) {
        LanguageIr.Expression operand = lower(expression.operand());
        LanguageIr.UnaryOperator operator = expression.operator();
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
                    operator == LanguageIr.UnaryOperator.NOT ? LanguageType.Scalar.BOOL : LanguageType.Scalar.NUMBER,
                    operand.dependencies(),
                    expression.span()
                )
            )
        );
    }

    private LanguageIr.Expression binary(Binary expression) {
        LanguageIr.Expression left = lower(expression.left());
        LanguageIr.Expression right = lower(expression.right());
        LanguageIr.BinaryOperator operator = expression.operator();
        LanguageType type = validate(operator, left, right, expression.span());
        return folded(node(new LanguageIr.Binary(operator, left, right, type, union(left, right), expression.span())));
    }

    private static LanguageType validate(
        LanguageIr.BinaryOperator operator,
        LanguageIr.Expression left,
        LanguageIr.Expression right,
        LanguageDiagnostic.SourceSpan span
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
                span
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
                span
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
            span
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
                binary.left() instanceof LanguageIr.Literal left && binary.right() instanceof LanguageIr.Literal right
            ) {
                try {
                    return literal(
                        EmbeddedLanguageEvaluator.compute(binary.operator(), left.value(), right.value()),
                        binary.span()
                    );
                } catch (IllegalArgumentException ignored) {
                    return expression;
                }
            }
        }
        if (expression instanceof LanguageIr.Unary unary && unary.operand() instanceof LanguageIr.Literal literal) {
            try {
                return literal(EmbeddedLanguageEvaluator.compute(unary.operator(), literal.value()), unary.span());
            } catch (IllegalArgumentException ignored) {
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

    private LanguageIr.Expression literal(@Nullable Object value, LanguageDiagnostic.SourceSpan span) {
        return node(new LanguageIr.Literal(value, typeOf(value), Set.of(), span));
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
        Set<String> result = new HashSet<>(left.dependencies());
        result.addAll(right.dependencies());
        return Set.copyOf(result);
    }

    private static void require(LanguageIr.Expression expression, LanguageType expected, String message) {
        if (!expression.type().equals(expected)) throw failure(
            LanguageDiagnostic.Category.TypeError,
            message,
            expression.span()
        );
    }

    private static LanguageIr.Expression requireExpression(LanguageIr.@Nullable Expression expression) {
        return Objects.requireNonNull(expression);
    }

    private static void requireMatchingBranches(LanguageIr.Expression whenTrue, LanguageIr.Expression whenFalse) {
        if (!whenTrue.type().equals(whenFalse.type())) throw failure(
            LanguageDiagnostic.Category.TypeError,
            "if branches must return the same type",
            whenFalse.span()
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
        StringBuilder result = new StringBuilder(text.length() - 2);
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
                        default -> throw syntax("invalid string escape", token);
                    }
                );
            }
        }
        return result.toString();
    }

    private static EmbeddedLanguageException syntax(String message, Token token) {
        return failure(LanguageDiagnostic.Category.SyntaxError, message, span(token));
    }

    private static LanguageDiagnostic.SourceSpan span(ParserRuleContext context) {
        return sourceSpan(context.getStart(), context.getStop());
    }

    private static LanguageDiagnostic.SourceSpan span(Token token) {
        return new LanguageDiagnostic.SourceSpan(
            token.getLine(),
            token.getCharPositionInLine(),
            token.getLine(),
            token.getCharPositionInLine() + token.getText().length()
        );
    }

    private static LanguageDiagnostic.SourceSpan sourceSpan(Token start, Token end) {
        return new LanguageDiagnostic.SourceSpan(
            start.getLine(),
            start.getCharPositionInLine(),
            end.getLine(),
            end.getCharPositionInLine() + end.getText().length()
        );
    }

    private static LanguageDiagnostic.SourceSpan sourceSpan(
        LanguageDiagnostic.SourceSpan start,
        LanguageDiagnostic.SourceSpan end
    ) {
        return new LanguageDiagnostic.SourceSpan(
            start.startLine(),
            start.startColumn(),
            end.endLine(),
            end.endColumn()
        );
    }

    private static LanguageDiagnostic.SourceSpan unknown() {
        return new LanguageDiagnostic.SourceSpan(1, 0, 1, 0);
    }

    private static EmbeddedLanguageException failure(
        LanguageDiagnostic.Category category,
        String message,
        LanguageDiagnostic.SourceSpan span
    ) {
        return EmbeddedLanguageCompiler.failure(category, message, span);
    }

    private sealed interface SyntaxNode permits Program, Block, Statement, Expression {}

    private record Program(List<Statement> statements) implements SyntaxNode {
        private Program {
            statements = List.copyOf(statements);
        }
    }

    private record Block(List<Statement> statements) implements SyntaxNode {
        private Block {
            statements = List.copyOf(statements);
        }
    }

    private sealed interface Statement extends SyntaxNode permits Constant, Returning, Conditional {
        LanguageDiagnostic.SourceSpan span();
    }

    private record Constant(String name, Expression value, LanguageDiagnostic.SourceSpan span) implements Statement {}

    private record Returning(Expression value, LanguageDiagnostic.SourceSpan span) implements Statement {}

    private record Conditional(
        Expression condition,
        List<Statement> whenTrue,
        @Nullable List<Statement> whenFalse,
        LanguageDiagnostic.SourceSpan span
    ) implements Statement {
        private Conditional {
            whenTrue = List.copyOf(whenTrue);
            whenFalse = whenFalse == null ? null : List.copyOf(whenFalse);
        }
    }

    private sealed interface Expression extends SyntaxNode permits Literal, Reference, ListExpression, Unary, Binary {
        LanguageDiagnostic.SourceSpan span();
    }

    private record Literal(Token token) implements Expression {
        @Override
        public LanguageDiagnostic.SourceSpan span() {
            return LanguageCompilerListener.span(this.token);
        }
    }

    private record Reference(String root, List<String> path, LanguageDiagnostic.SourceSpan span) implements Expression {
        private Reference {
            path = List.copyOf(path);
        }
    }

    private record ListExpression(List<Expression> values, LanguageDiagnostic.SourceSpan span) implements Expression {
        private ListExpression {
            values = List.copyOf(values);
        }
    }

    private record Unary(
        LanguageIr.UnaryOperator operator,
        Expression operand,
        LanguageDiagnostic.SourceSpan span
    ) implements Expression {}

    private record Binary(
        LanguageIr.BinaryOperator operator,
        Expression left,
        Expression right,
        LanguageDiagnostic.SourceSpan span
    ) implements Expression {}

    private static final class Scope {

        private final @Nullable Scope parent;
        private final Map<String, LanguageIr.Expression> values = new HashMap<>();

        private Scope(@Nullable Scope parent) {
            this.parent = parent;
        }

        private void put(String name, LanguageIr.Expression value) {
            this.values.put(name, value);
        }

        private LanguageIr.@Nullable Expression lookup(String name) {
            LanguageIr.Expression value = this.values.get(name);
            return value != null ? value : this.parent == null ? null : this.parent.lookup(name);
        }
    }
}
