package io.taskmigo.embeddedlanguage;

import io.taskmigo.embeddedlanguage.antlr.EmbeddedLanguageParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

/// Converts the validated ANTLR token stream directly into language-owned IR.
///
/// This frontend deliberately does not retain or consume ANTLR parse-tree nodes. Its compact recursive-descent pass
/// validates the token sequence and performs the semantic conversion needed by the compiler.
@SuppressWarnings({
    "checkstyle:NeedBraces",
    "checkstyle:OverloadMethodsDeclarationOrder",
    "checkstyle:UnnecessaryFullyQualifiedType",
})
final class FastEmbeddedLanguageCompiler {

    private final EnvironmentSchema schema;
    private final CompilerLimits limits;
    private final List<Token> tokens;
    private final List<Scope> scopes = new ArrayList<>();
    private int cursor;
    private int nodes;
    private int syntaxNesting;

    FastEmbeddedLanguageCompiler(EnvironmentSchema schema, CompilerLimits limits, CommonTokenStream stream) {
        this.schema = schema;
        this.limits = limits;
        this.tokens = new ArrayList<>();
        for (int index = 0; index < stream.size(); index++) {
            Token token = stream.get(index);
            if (token.getChannel() == Token.DEFAULT_CHANNEL) this.tokens.add(token);
        }
    }

    LanguageIr.Expression compile() {
        List<Statement> statements = new ArrayList<>();
        while (!at(Token.EOF)) statements.add(statement());
        return sequence(statements, new Scope(null), 0, null, new HashSet<>());
    }

    private Statement statement() {
        return switch (current().getType()) {
            case EmbeddedLanguageParser.CONST -> constant();
            case EmbeddedLanguageParser.RETURN -> returning();
            case EmbeddedLanguageParser.IF -> conditional();
            default -> throw syntax("expected a statement", current());
        };
    }

    private Constant constant() {
        Token start = expect(EmbeddedLanguageParser.CONST);
        Token name = expect(EmbeddedLanguageParser.IDENT);
        expect(EmbeddedLanguageParser.ASSIGN);
        Expression value = expression();
        Token end = expect(EmbeddedLanguageParser.SEMICOLON);
        return new Constant(name.getText(), value, sourceSpan(start, end));
    }

    private Returning returning() {
        Token start = expect(EmbeddedLanguageParser.RETURN);
        Expression value = expression();
        Token end = expect(EmbeddedLanguageParser.SEMICOLON);
        return new Returning(value, sourceSpan(start, end));
    }

    private Conditional conditional() {
        Token start = expect(EmbeddedLanguageParser.IF);
        expect(EmbeddedLanguageParser.LPAREN);
        Expression condition = expression();
        expect(EmbeddedLanguageParser.RPAREN);
        List<Statement> whenTrue = block();
        @Nullable
        List<Statement> whenFalse = null;
        if (accept(EmbeddedLanguageParser.ELSE)) {
            whenFalse = current().getType() == EmbeddedLanguageParser.IF ? List.of(conditional()) : block();
        }
        Token end = previous();
        return new Conditional(condition, whenTrue, whenFalse, sourceSpan(start, end));
    }

    private List<Statement> block() {
        expect(EmbeddedLanguageParser.LBRACE);
        List<Statement> statements = new ArrayList<>();
        while (!at(EmbeddedLanguageParser.RBRACE) && !at(Token.EOF)) statements.add(statement());
        expect(EmbeddedLanguageParser.RBRACE);
        return List.copyOf(statements);
    }

    private Expression expression() {
        return or();
    }

    private Expression or() {
        Expression result = and();
        while (accept(EmbeddedLanguageParser.OR)) {
            Expression right = and();
            result = new Binary(LanguageIr.BinaryOperator.OR, result, right, sourceSpan(result.span(), right.span()));
        }
        return result;
    }

    private Expression and() {
        Expression result = equality();
        while (accept(EmbeddedLanguageParser.AND)) {
            Expression right = equality();
            result = new Binary(LanguageIr.BinaryOperator.AND, result, right, sourceSpan(result.span(), right.span()));
        }
        return result;
    }

    private Expression equality() {
        Expression result = comparison();
        while (at(EmbeddedLanguageParser.EQUAL) || at(EmbeddedLanguageParser.NOT_EQUAL)) {
            LanguageIr.BinaryOperator operator =
                take().getType() == EmbeddedLanguageParser.EQUAL
                    ? LanguageIr.BinaryOperator.EQUAL
                    : LanguageIr.BinaryOperator.NOT_EQUAL;
            Expression right = comparison();
            result = new Binary(operator, result, right, sourceSpan(result.span(), right.span()));
        }
        return result;
    }

    private Expression comparison() {
        Expression result = membership();
        while (
            at(EmbeddedLanguageParser.LESS) ||
            at(EmbeddedLanguageParser.LESS_EQUAL) ||
            at(EmbeddedLanguageParser.GREATER) ||
            at(EmbeddedLanguageParser.GREATER_EQUAL)
        ) {
            LanguageIr.BinaryOperator operator = switch (take().getType()) {
                case EmbeddedLanguageParser.LESS -> LanguageIr.BinaryOperator.LESS;
                case EmbeddedLanguageParser.LESS_EQUAL -> LanguageIr.BinaryOperator.LESS_OR_EQUAL;
                case EmbeddedLanguageParser.GREATER -> LanguageIr.BinaryOperator.GREATER;
                default -> LanguageIr.BinaryOperator.GREATER_OR_EQUAL;
            };
            Expression right = membership();
            result = new Binary(operator, result, right, sourceSpan(result.span(), right.span()));
        }
        return result;
    }

    private Expression membership() {
        Expression result = additive();
        if (accept(EmbeddedLanguageParser.IN)) {
            Expression right = additive();
            result = new Binary(LanguageIr.BinaryOperator.IN, result, right, sourceSpan(result.span(), right.span()));
        }
        return result;
    }

    private Expression additive() {
        Expression result = multiplicative();
        while (at(EmbeddedLanguageParser.PLUS) || at(EmbeddedLanguageParser.MINUS)) {
            LanguageIr.BinaryOperator operator =
                take().getType() == EmbeddedLanguageParser.PLUS
                    ? LanguageIr.BinaryOperator.ADD
                    : LanguageIr.BinaryOperator.SUBTRACT;
            Expression right = multiplicative();
            result = new Binary(operator, result, right, sourceSpan(result.span(), right.span()));
        }
        return result;
    }

    private Expression multiplicative() {
        Expression result = unary();
        while (
            at(EmbeddedLanguageParser.STAR) || at(EmbeddedLanguageParser.SLASH) || at(EmbeddedLanguageParser.PERCENT)
        ) {
            LanguageIr.BinaryOperator operator = switch (take().getType()) {
                case EmbeddedLanguageParser.STAR -> LanguageIr.BinaryOperator.MULTIPLY;
                case EmbeddedLanguageParser.SLASH -> LanguageIr.BinaryOperator.DIVIDE;
                default -> LanguageIr.BinaryOperator.MODULO;
            };
            Expression right = unary();
            result = new Binary(operator, result, right, sourceSpan(result.span(), right.span()));
        }
        return result;
    }

    private Expression unary() {
        if (++syntaxNesting > limits.maxSyntaxDepth()) {
            throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "program syntax depth exceeds the limit",
                span(current())
            );
        }
        try {
            if (at(EmbeddedLanguageParser.NOT) || at(EmbeddedLanguageParser.PLUS) || at(EmbeddedLanguageParser.MINUS)) {
                Token operator = take();
                Expression operand = unary();
                return new Unary(
                    switch (operator.getType()) {
                        case EmbeddedLanguageParser.NOT -> LanguageIr.UnaryOperator.NOT;
                        case EmbeddedLanguageParser.PLUS -> LanguageIr.UnaryOperator.PLUS;
                        default -> LanguageIr.UnaryOperator.MINUS;
                    },
                    operand,
                    sourceSpan(span(operator), operand.span())
                );
            }
            return primary();
        } finally {
            syntaxNesting--;
        }
    }

    private Expression primary() {
        if (accept(EmbeddedLanguageParser.LPAREN)) {
            Expression result = expression();
            expect(EmbeddedLanguageParser.RPAREN);
            return result;
        }
        if (at(EmbeddedLanguageParser.LBRACKET)) return listExpression();
        if (at(EmbeddedLanguageParser.TRUE) || at(EmbeddedLanguageParser.FALSE) || at(EmbeddedLanguageParser.NULL)) {
            return new Literal(take());
        }
        if (at(EmbeddedLanguageParser.NUMBER) || at(EmbeddedLanguageParser.STRING)) return new Literal(take());
        if (at(EmbeddedLanguageParser.IDENT)) return reference();
        throw syntax("mismatched input '" + current().getText() + "' expecting an expression", current());
    }

    private Expression reference() {
        Token start = expect(EmbeddedLanguageParser.IDENT);
        List<String> path = new ArrayList<>();
        while (accept(EmbeddedLanguageParser.DOT)) path.add(expect(EmbeddedLanguageParser.IDENT).getText());
        return new Reference(start.getText(), List.copyOf(path), sourceSpan(start, previous()));
    }

    private Expression listExpression() {
        Token start = expect(EmbeddedLanguageParser.LBRACKET);
        List<Expression> values = new ArrayList<>();
        if (!at(EmbeddedLanguageParser.RBRACKET)) {
            values.add(expression());
            while (accept(EmbeddedLanguageParser.COMMA)) values.add(expression());
        }
        Token end = expect(EmbeddedLanguageParser.RBRACKET);
        return new ListExpression(List.copyOf(values), sourceSpan(start, end));
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

    private Token current() {
        return this.tokens.get(this.cursor);
    }

    private Token previous() {
        return this.tokens.get(this.cursor - 1);
    }

    private Token take() {
        return this.tokens.get(this.cursor++);
    }

    private boolean at(int type) {
        return current().getType() == type;
    }

    private boolean accept(int type) {
        if (!at(type)) return false;
        cursor++;
        return true;
    }

    private Token expect(int type) {
        if (!at(type)) throw syntax("unexpected token", current());
        return take();
    }

    private static EmbeddedLanguageException syntax(String message, Token token) {
        return failure(LanguageDiagnostic.Category.SyntaxError, message, span(token));
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

    private sealed interface Statement permits Constant, Returning, Conditional {
        LanguageDiagnostic.SourceSpan span();
    }

    private record Constant(String name, Expression value, LanguageDiagnostic.SourceSpan span) implements Statement {}

    private record Returning(Expression value, LanguageDiagnostic.SourceSpan span) implements Statement {}

    private record Conditional(
        Expression condition,
        List<Statement> whenTrue,
        @Nullable List<Statement> whenFalse,
        LanguageDiagnostic.SourceSpan span
    ) implements Statement {}

    private sealed interface Expression permits Literal, Reference, ListExpression, Unary, Binary {
        LanguageDiagnostic.SourceSpan span();
    }

    private record Literal(Token token) implements Expression {
        @Override
        public LanguageDiagnostic.SourceSpan span() {
            return FastEmbeddedLanguageCompiler.span(this.token);
        }
    }

    private record Reference(
        String root,
        List<String> path,
        LanguageDiagnostic.SourceSpan span
    ) implements Expression {}

    private record ListExpression(List<Expression> values, LanguageDiagnostic.SourceSpan span) implements Expression {}

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
