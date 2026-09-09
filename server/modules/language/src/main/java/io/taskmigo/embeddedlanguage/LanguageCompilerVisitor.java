package io.taskmigo.embeddedlanguage;

import io.taskmigo.embeddedlanguage.antlr.EmbeddedLanguageBaseVisitor;
import io.taskmigo.embeddedlanguage.antlr.EmbeddedLanguageParser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.jspecify.annotations.Nullable;

/// Converts the generated ANTLR parse tree into the typed language-owned Semantic AST.
///
/// Parsing remains owned by the canonical ANTLR grammar. This visitor builds a compact private syntax model and then
/// performs binding, type checking, control-flow normalization, dependency analysis, and constant folding.
@SuppressWarnings({
    "checkstyle:NeedBraces",
    "checkstyle:OverloadMethodsDeclarationOrder",
    "checkstyle:UnnecessaryFullyQualifiedType",
})
final class LanguageCompilerVisitor extends EmbeddedLanguageBaseVisitor<Object> {

    private final EnvironmentSchema schema;
    private final CompilerLimits limits;
    private final CompilationProfile profile;
    private final List<Scope> scopes = new ArrayList<>();
    private int nodes;
    private int syntaxNesting;
    private int quantifierNesting;
    private int lambdaNesting;

    LanguageCompilerVisitor(EnvironmentSchema schema, CompilerLimits limits, CompilationProfile profile) {
        this.schema = schema;
        this.limits = limits;
        this.profile = profile;
    }

    SemanticAst.Expression compile(EmbeddedLanguageParser.ProgramContext context) {
        Program program = (Program) visitProgram(context);
        return sequence(program.statements(), new Scope(null), 0, null, new HashSet<>());
    }

    SemanticAst.Expression compile(EmbeddedLanguageParser.ExpressionSourceContext context) {
        scopes.add(new Scope(null));
        try {
            return lower(expression(context.expression()));
        } finally {
            scopes.removeLast();
        }
    }

    @Override
    public SyntaxNode visitProgram(EmbeddedLanguageParser.ProgramContext context) {
        return new Program(context.statement().stream().map(this::statement).toList());
    }

    @Override
    public SyntaxNode visitStatement(EmbeddedLanguageParser.StatementContext context) {
        return (SyntaxNode) visit(context.getChild(0));
    }

    @Override
    public SyntaxNode visitBlock(EmbeddedLanguageParser.BlockContext context) {
        return new Block(context.statement().stream().map(this::statement).toList());
    }

    @Override
    public SyntaxNode visitConstDecl(EmbeddedLanguageParser.ConstDeclContext context) {
        requireFeature(CompilationFeature.LOCAL_BINDINGS, context.getStart());
        return new Constant(context.IDENT().getText(), expression(context.expression()), span(context));
    }

    @Override
    public SyntaxNode visitReturnStatement(EmbeddedLanguageParser.ReturnStatementContext context) {
        return new Returning(expression(context.expression()), span(context));
    }

    @Override
    public SyntaxNode visitIfStatement(EmbeddedLanguageParser.IfStatementContext context) {
        requireFeature(CompilationFeature.CONDITIONAL_CONTROL_FLOW, context.getStart());
        Block whenTrue = (Block) visitBlock(context.block(0));
        @Nullable
        List<Statement> whenFalse = null;
        if (context.ELSE() != null) {
            if (context.ifStatement() != null) {
                whenFalse = List.of((Statement) visitIfStatement(context.ifStatement()));
            } else {
                whenFalse = ((Block) visitBlock(context.block(1))).statements();
            }
        }
        return new Conditional(expression(context.expression()), whenTrue.statements(), whenFalse, span(context));
    }

    @Override
    public SyntaxNode visitExpression(EmbeddedLanguageParser.ExpressionContext context) {
        return visitOrExpression(context.orExpression());
    }

    @Override
    public SyntaxNode visitOrExpression(EmbeddedLanguageParser.OrExpressionContext context) {
        return chain(context.andExpression(), context, operator -> SemanticAst.BinaryOperator.OR);
    }

    @Override
    public SyntaxNode visitAndExpression(EmbeddedLanguageParser.AndExpressionContext context) {
        return chain(context.equalityExpression(), context, operator -> SemanticAst.BinaryOperator.AND);
    }

    @Override
    public SyntaxNode visitEqualityExpression(EmbeddedLanguageParser.EqualityExpressionContext context) {
        return chain(
            context.comparisonExpression(),
            context,
            operator ->
                switch (operator) {
                    case "==" -> SemanticAst.BinaryOperator.EQUAL;
                    case "!=" -> SemanticAst.BinaryOperator.NOT_EQUAL;
                    default -> throw new IllegalStateException("unsupported equality operator");
                }
        );
    }

    @Override
    public SyntaxNode visitComparisonExpression(EmbeddedLanguageParser.ComparisonExpressionContext context) {
        return chain(
            context.membershipExpression(),
            context,
            operator ->
                switch (operator) {
                    case "<" -> SemanticAst.BinaryOperator.LESS;
                    case "<=" -> SemanticAst.BinaryOperator.LESS_OR_EQUAL;
                    case ">" -> SemanticAst.BinaryOperator.GREATER;
                    case ">=" -> SemanticAst.BinaryOperator.GREATER_OR_EQUAL;
                    default -> throw new IllegalStateException("unsupported comparison operator");
                }
        );
    }

    @Override
    public SyntaxNode visitMembershipExpression(EmbeddedLanguageParser.MembershipExpressionContext context) {
        Expression left = expression(context.additiveExpression(0));
        if (context.additiveExpression().size() == 1) return left;
        Expression right = expression(context.additiveExpression(1));
        return new Binary(SemanticAst.BinaryOperator.IN, left, right, sourceSpan(left.span(), right.span()));
    }

    @Override
    public SyntaxNode visitAdditiveExpression(EmbeddedLanguageParser.AdditiveExpressionContext context) {
        return chain(
            context.multiplicativeExpression(),
            context,
            operator ->
                switch (operator) {
                    case "+" -> SemanticAst.BinaryOperator.ADD;
                    case "-" -> SemanticAst.BinaryOperator.SUBTRACT;
                    default -> throw new IllegalStateException("unsupported additive operator");
                }
        );
    }

    @Override
    public SyntaxNode visitMultiplicativeExpression(EmbeddedLanguageParser.MultiplicativeExpressionContext context) {
        return chain(
            context.unaryExpression(),
            context,
            operator ->
                switch (operator) {
                    case "*" -> SemanticAst.BinaryOperator.MULTIPLY;
                    case "/" -> SemanticAst.BinaryOperator.DIVIDE;
                    case "%" -> SemanticAst.BinaryOperator.MODULO;
                    default -> throw new IllegalStateException("unsupported multiplicative operator");
                }
        );
    }

    @Override
    public SyntaxNode visitUnaryExpression(EmbeddedLanguageParser.UnaryExpressionContext context) {
        if (++syntaxNesting > this.limits.maxSyntaxDepth()) {
            throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "program syntax depth exceeds the limit",
                span(context)
            );
        }
        try {
            if (context.primary() != null) return visitPrimary(context.primary());
            SemanticAst.UnaryOperator operator = switch (context.getChild(0).getText()) {
                case "!" -> SemanticAst.UnaryOperator.NOT;
                case "+" -> SemanticAst.UnaryOperator.PLUS;
                case "-" -> SemanticAst.UnaryOperator.MINUS;
                default -> throw new IllegalStateException("unsupported unary operator");
            };
            return new Unary(operator, expression(context.unaryExpression()), span(context));
        } finally {
            syntaxNesting--;
        }
    }

    @Override
    public SyntaxNode visitPrimary(EmbeddedLanguageParser.PrimaryContext context) {
        if (context.literal() != null) return visitLiteral(context.literal());
        if (context.listLiteral() != null) return visitListLiteral(context.listLiteral());
        if (context.reference() != null) return visitReference(context.reference());
        if (context.quantifierExpression() != null) return visitQuantifierExpression(context.quantifierExpression());
        if (context.lengthExpression() != null) return visitLengthExpression(context.lengthExpression());
        return visitExpression(context.expression());
    }

    @Override
    public SyntaxNode visitQuantifierExpression(EmbeddedLanguageParser.QuantifierExpressionContext context) {
        requireFeature(CompilationFeature.COLLECTION_QUANTIFIERS, context.getStart());
        if (++quantifierNesting > limits.maxQuantifierDepth()) {
            throw failure(
                LanguageDiagnostic.Category.ComplexityError,
                "quantifier nesting exceeds the limit",
                span(context)
            );
        }
        try {
            SemanticAst.QuantifierOperator operator = switch (context.quantifier().getStart().getType()) {
                case EmbeddedLanguageParser.ALL -> SemanticAst.QuantifierOperator.ALL;
                case EmbeddedLanguageParser.ANY -> SemanticAst.QuantifierOperator.ANY;
                case EmbeddedLanguageParser.NONE -> SemanticAst.QuantifierOperator.NONE;
                default -> throw syntax("invalid quantifier", context.getStart());
            };
            return new Quantifier(
                operator,
                expression(context.expression(0)),
                context.IDENT().getText(),
                expression(context.expression(1)),
                span(context)
            );
        } finally {
            quantifierNesting--;
        }
    }

    @Override
    public SyntaxNode visitLengthExpression(EmbeddedLanguageParser.LengthExpressionContext context) {
        requireFeature(CompilationFeature.LENGTH_INTRINSIC, context.getStart());
        return new LengthExpression(expression(context.expression()), span(context));
    }

    @Override
    public SyntaxNode visitReference(EmbeddedLanguageParser.ReferenceContext context) {
        List<String> names = context
            .IDENT()
            .stream()
            .map(node -> node.getText())
            .toList();
        return new Reference(names.getFirst(), names.subList(1, names.size()), span(context));
    }

    @Override
    public SyntaxNode visitListLiteral(EmbeddedLanguageParser.ListLiteralContext context) {
        return new ListExpression(context.expression().stream().map(this::expression).toList(), span(context));
    }

    @Override
    public SyntaxNode visitLiteral(EmbeddedLanguageParser.LiteralContext context) {
        return new Literal(context.getStart());
    }

    private Statement statement(EmbeddedLanguageParser.StatementContext context) {
        return (Statement) visitStatement(context);
    }

    private Expression expression(ParserRuleContext context) {
        return (Expression) visit(context);
    }

    private Expression chain(
        List<? extends ParserRuleContext> operands,
        ParserRuleContext context,
        java.util.function.Function<String, SemanticAst.BinaryOperator> operator
    ) {
        Expression result = expression(operands.getFirst());
        for (int index = 1; index < operands.size(); index++) {
            Expression right = expression(operands.get(index));
            result = new Binary(
                operator.apply(context.getChild(index * 2 - 1).getText()),
                result,
                right,
                sourceSpan(result.span(), right.span())
            );
        }
        return result;
    }

    private SemanticAst.Expression sequence(
        List<Statement> statements,
        Scope environment,
        int blockDepth,
        SemanticAst.@Nullable Expression continuation,
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
                SemanticAst.Expression rest =
                    hasFollowing || !hasElse
                        ? sequence(
                              statements.subList(index + 1, statements.size()),
                              new Scope(environment),
                              blockDepth,
                              continuation,
                              new HashSet<>(declared)
                          )
                        : continuation;
                SemanticAst.Expression whenTrue = sequence(
                    conditional.whenTrue(),
                    new Scope(environment),
                    blockDepth + 1,
                    rest,
                    new HashSet<>()
                );
                SemanticAst.Expression whenFalse = hasElse
                    ? lowerElse(Objects.requireNonNull(conditional.whenFalse()), environment, blockDepth + 1, rest)
                    : requireExpression(rest);
                SemanticAst.Expression condition = lower(conditional.condition());
                require(condition, LanguageType.Scalar.BOOL, "if condition must be Bool");
                requireMatchingBranches(whenTrue, whenFalse);
                return folded(node(new SemanticAst.Conditional(condition, whenTrue, whenFalse)));
            }
            if (continuation == null) throw failure(
                LanguageDiagnostic.Category.ControlFlowError,
                "program must return a value on every path",
                unknown()
            );
            return continuation;
        } finally {
            scopes.removeLast();
        }
    }

    private SemanticAst.Expression lowerElse(
        List<Statement> statements,
        Scope environment,
        int blockDepth,
        SemanticAst.@Nullable Expression continuation
    ) {
        if (statements.size() == 1 && statements.getFirst() instanceof Conditional conditional) {
            return lowerConditional(conditional, environment, blockDepth, continuation);
        }
        return sequence(statements, new Scope(environment), blockDepth, continuation, new HashSet<>());
    }

    private SemanticAst.Expression lowerConditional(
        Conditional conditional,
        Scope environment,
        int blockDepth,
        SemanticAst.@Nullable Expression continuation
    ) {
        SemanticAst.Expression whenTrue = sequence(
            conditional.whenTrue(),
            new Scope(environment),
            blockDepth + 1,
            continuation,
            new HashSet<>()
        );
        SemanticAst.Expression whenFalse =
            conditional.whenFalse() == null
                ? requireExpression(continuation)
                : lowerElse(Objects.requireNonNull(conditional.whenFalse()), environment, blockDepth + 1, continuation);
        SemanticAst.Expression condition = lower(conditional.condition());
        require(condition, LanguageType.Scalar.BOOL, "if condition must be Bool");
        requireMatchingBranches(whenTrue, whenFalse);
        return folded(node(new SemanticAst.Conditional(condition, whenTrue, whenFalse)));
    }

    private SemanticAst.Expression lower(Expression expression) {
        return switch (expression) {
            case Literal literal -> literal(literal.token());
            case Reference reference -> reference(reference);
            case ListExpression list -> list(list);
            case Unary unary -> unary(unary);
            case Binary binary -> binary(binary);
            case Quantifier quantifier -> quantifier(quantifier);
            case LengthExpression length -> length(length);
        };
    }

    private SemanticAst.Expression literal(Token token) {
        Object value = switch (token.getType()) {
            case EmbeddedLanguageParser.TRUE -> true;
            case EmbeddedLanguageParser.FALSE -> false;
            case EmbeddedLanguageParser.NULL -> null;
            case EmbeddedLanguageParser.NUMBER -> parseNumber(token);
            case EmbeddedLanguageParser.STRING -> parseString(token);
            default -> throw syntax("invalid literal", token);
        };
        return node(new SemanticAst.Literal(value, typeOf(value), Set.of(), span(token)));
    }

    private SemanticAst.Expression reference(Reference reference) {
        SemanticAst.@Nullable Expression local = scopes.getLast().lookup(reference.root());
        if (local != null) {
            if (reference.path().isEmpty()) return local;
            if (local instanceof SemanticAst.Reference localReference && localReference.root().equals("__lambda__")) {
                List<String> path = new ArrayList<>(localReference.path());
                path.addAll(reference.path());
                return node(
                    new SemanticAst.Reference(
                        localReference.root(),
                        path,
                        resolveLocalPath(localReference.type(), reference.path()),
                        localReference.nullable(),
                        true,
                        Set.of(),
                        reference.span()
                    )
                );
            }
        }
        EnvironmentSchema.Field field = schema.resolve(reference.root(), reference.path());
        if (field == null) throw failure(
            LanguageDiagnostic.Category.BindingError,
            "unknown program reference: " + reference.root() + String.join(".", reference.path()),
            reference.span()
        );
        return node(
            new SemanticAst.Reference(
                reference.root(),
                reference.path(),
                field.type(),
                field.nullable(),
                field.symbolic(),
                Set.of(reference.root()),
                reference.span()
            )
        );
    }

    private SemanticAst.Expression list(ListExpression expression) {
        requireFeature(CompilationFeature.LIST_LITERALS, expression.span());
        if (expression.values().size() > limits.maxListElements()) throw failure(
            LanguageDiagnostic.Category.ComplexityError,
            "list literal exceeds the element limit",
            expression.span()
        );
        List<SemanticAst.Expression> values = new ArrayList<>(expression.values().size());
        Set<String> dependencies = new HashSet<>();
        for (Expression value : expression.values()) {
            SemanticAst.Expression lowered = lower(value);
            values.add(lowered);
            dependencies.addAll(lowered.dependencies());
        }
        LanguageType element = values.isEmpty() ? LanguageType.Scalar.NULL : values.getFirst().type();
        for (SemanticAst.Expression value : values)
            if (!value.type().equals(element)) throw failure(
                LanguageDiagnostic.Category.TypeError,
                "list elements must have one homogeneous type",
                value.span()
            );
        return node(
            new SemanticAst.ListLiteral(
                values,
                new LanguageType.ListType(element),
                Set.copyOf(dependencies),
                expression.span()
            )
        );
    }

    private SemanticAst.Expression unary(Unary expression) {
        SemanticAst.Expression operand = lower(expression.operand());
        SemanticAst.UnaryOperator operator = expression.operator();
        requireFeature(
            operator == SemanticAst.UnaryOperator.NOT
                ? CompilationFeature.LOGICAL_OPERATORS
                : CompilationFeature.ARITHMETIC_OPERATORS,
            expression.span()
        );
        require(
            operand,
            operator == SemanticAst.UnaryOperator.NOT ? LanguageType.Scalar.BOOL : LanguageType.Scalar.NUMBER,
            "unary operator has an incompatible operand"
        );
        return folded(
            node(
                new SemanticAst.Unary(
                    operator,
                    operand,
                    operator == SemanticAst.UnaryOperator.NOT ? LanguageType.Scalar.BOOL : LanguageType.Scalar.NUMBER,
                    operand.dependencies(),
                    expression.span()
                )
            )
        );
    }

    private SemanticAst.Expression binary(Binary expression) {
        SemanticAst.Expression left = lower(expression.left());
        SemanticAst.Expression right = lower(expression.right());
        SemanticAst.BinaryOperator operator = expression.operator();
        requireFeature(feature(operator), expression.span());
        LanguageType type = validate(operator, left, right, expression.span());
        return folded(node(new SemanticAst.Binary(operator, left, right, type, union(left, right), expression.span())));
    }

    private SemanticAst.Expression quantifier(Quantifier expression) {
        SemanticAst.Expression collection = lower(expression.collection());
        if (!(collection.type() instanceof LanguageType.ListType list)) {
            throw failure(LanguageDiagnostic.Category.TypeError, "quantifier requires a List", expression.span());
        }
        SemanticAst.Expression predicate;
        scopes.add(new Scope(scopes.isEmpty() ? null : scopes.getLast()));
        lambdaNesting++;
        try {
            if (lambdaNesting > limits.maxLambdaDepth()) {
                throw failure(
                    LanguageDiagnostic.Category.ComplexityError,
                    "lambda nesting exceeds the limit",
                    expression.span()
                );
            }
            scopes
                .getLast()
                .put(
                    expression.elementName(),
                    new SemanticAst.Reference(
                        "__lambda__",
                        List.of(expression.elementName()),
                        list.elementType(),
                        false,
                        false,
                        Set.of(),
                        expression.span()
                    )
                );
            predicate = lower(expression.predicate());
        } finally {
            lambdaNesting--;
            scopes.removeLast();
        }
        require(predicate, LanguageType.Scalar.BOOL, "quantifier predicate must be Bool");
        return folded(
            node(
                new SemanticAst.Quantifier(
                    expression.operator(),
                    collection,
                    expression.elementName(),
                    predicate,
                    LanguageType.Scalar.BOOL,
                    union(collection, predicate),
                    expression.span()
                )
            )
        );
    }

    private SemanticAst.Expression length(LengthExpression expression) {
        SemanticAst.Expression operand = lower(expression.operand());
        if (
            operand.type() != LanguageType.Scalar.STRING && !(operand.type() instanceof LanguageType.ListType)
        ) throw failure(LanguageDiagnostic.Category.TypeError, "len requires a String or List", expression.span());
        if (operand.nullable()) throw failure(
            LanguageDiagnostic.Category.TypeError,
            "len does not accept nullable values",
            expression.span()
        );
        return node(
            new SemanticAst.Length(operand, LanguageType.Scalar.NUMBER, operand.dependencies(), expression.span())
        );
    }

    private static LanguageType resolveLocalPath(LanguageType type, List<String> path) {
        LanguageType current = type;
        for (String segment : path) {
            if (!(current instanceof LanguageType.StructuredType structured)) {
                throw failure(LanguageDiagnostic.Category.BindingError, "unknown lambda path: " + segment, unknown());
            }
            EnvironmentSchema.Field field = structured.field(segment);
            if (field == null) throw failure(
                LanguageDiagnostic.Category.BindingError,
                "unknown lambda path: " + segment,
                unknown()
            );
            current = field.type();
        }
        return current;
    }

    private CompilationFeature feature(SemanticAst.BinaryOperator operator) {
        return switch (operator) {
            case AND, OR -> CompilationFeature.LOGICAL_OPERATORS;
            case EQUAL, NOT_EQUAL -> CompilationFeature.EQUALITY_OPERATORS;
            case GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL -> CompilationFeature.ORDERING_OPERATORS;
            case IN -> CompilationFeature.MEMBERSHIP;
            case ADD, SUBTRACT, MULTIPLY, DIVIDE, MODULO -> CompilationFeature.ARITHMETIC_OPERATORS;
        };
    }

    private static LanguageType validate(
        SemanticAst.BinaryOperator operator,
        SemanticAst.Expression left,
        SemanticAst.Expression right,
        LanguageDiagnostic.SourceSpan span
    ) {
        if (operator == SemanticAst.BinaryOperator.AND || operator == SemanticAst.BinaryOperator.OR) {
            require(left, LanguageType.Scalar.BOOL, "boolean operators require Bool");
            require(right, LanguageType.Scalar.BOOL, "boolean operators require Bool");
            return LanguageType.Scalar.BOOL;
        }
        if (
            operator == SemanticAst.BinaryOperator.ADD ||
            operator == SemanticAst.BinaryOperator.SUBTRACT ||
            operator == SemanticAst.BinaryOperator.MULTIPLY ||
            operator == SemanticAst.BinaryOperator.DIVIDE ||
            operator == SemanticAst.BinaryOperator.MODULO
        ) {
            require(left, LanguageType.Scalar.NUMBER, "arithmetic requires Number");
            require(right, LanguageType.Scalar.NUMBER, "arithmetic requires Number");
            return LanguageType.Scalar.NUMBER;
        }
        if (operator == SemanticAst.BinaryOperator.IN) {
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
            operator == SemanticAst.BinaryOperator.GREATER ||
            operator == SemanticAst.BinaryOperator.GREATER_OR_EQUAL ||
            operator == SemanticAst.BinaryOperator.LESS ||
            operator == SemanticAst.BinaryOperator.LESS_OR_EQUAL
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
            (left.type() == LanguageType.Scalar.NULL && right.nullable()) ||
            (right.type() == LanguageType.Scalar.NULL && left.nullable())
        ) return LanguageType.Scalar.BOOL;
        throw failure(
            LanguageDiagnostic.Category.TypeError,
            "equality requires matching types or nullable values",
            span
        );
    }

    private SemanticAst.Expression folded(SemanticAst.Expression expression) {
        if (expression instanceof SemanticAst.Binary binary) {
            if (
                binary.operator() == SemanticAst.BinaryOperator.AND &&
                binary.left() instanceof SemanticAst.Literal literal &&
                literal.value() instanceof Boolean value
            ) return value ? binary.right() : literal;
            if (
                binary.operator() == SemanticAst.BinaryOperator.OR &&
                binary.left() instanceof SemanticAst.Literal literal &&
                literal.value() instanceof Boolean value
            ) return value ? literal : binary.right();
            if (
                binary.left() instanceof SemanticAst.Literal left && binary.right() instanceof SemanticAst.Literal right
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
        if (expression instanceof SemanticAst.Unary unary && unary.operand() instanceof SemanticAst.Literal literal) {
            try {
                return literal(EmbeddedLanguageEvaluator.compute(unary.operator(), literal.value()), unary.span());
            } catch (IllegalArgumentException ignored) {
                return expression;
            }
        }
        if (
            expression instanceof SemanticAst.Conditional conditional &&
            conditional.condition() instanceof SemanticAst.Literal literal &&
            literal.value() instanceof Boolean value
        ) return value ? conditional.whenTrue() : conditional.whenFalse();
        return expression;
    }

    private SemanticAst.Expression literal(@Nullable Object value, LanguageDiagnostic.SourceSpan span) {
        return node(new SemanticAst.Literal(value, typeOf(value), Set.of(), span));
    }

    private SemanticAst.Expression node(SemanticAst.Expression expression) {
        if (++nodes > limits.maxSemanticAstNodes()) throw failure(
            LanguageDiagnostic.Category.ComplexityError,
            "program Semantic AST node count exceeds the limit",
            expression.span()
        );
        return expression;
    }

    private static Set<String> union(SemanticAst.Expression left, SemanticAst.Expression right) {
        Set<String> result = new HashSet<>(left.dependencies());
        result.addAll(right.dependencies());
        return Set.copyOf(result);
    }

    private static void require(SemanticAst.Expression expression, LanguageType expected, String message) {
        if (!expression.type().equals(expected)) throw failure(
            LanguageDiagnostic.Category.TypeError,
            message,
            expression.span()
        );
    }

    private static SemanticAst.Expression requireExpression(SemanticAst.@Nullable Expression expression) {
        return Objects.requireNonNull(expression);
    }

    private static void requireMatchingBranches(SemanticAst.Expression whenTrue, SemanticAst.Expression whenFalse) {
        if (
            !whenTrue.type().equals(whenFalse.type()) &&
            whenTrue.type() != LanguageType.Scalar.NULL &&
            whenFalse.type() != LanguageType.Scalar.NULL
        ) throw failure(
            LanguageDiagnostic.Category.TypeError,
            "if branches must return compatible types",
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

    private void requireFeature(CompilationFeature feature, Token token) {
        if (!profile.enables(feature)) throw failure(
            LanguageDiagnostic.Category.FeatureError,
            "feature is disabled: " + feature,
            span(token)
        );
    }

    private void requireFeature(CompilationFeature feature, LanguageDiagnostic.SourceSpan span) {
        if (!profile.enables(feature)) throw failure(
            LanguageDiagnostic.Category.FeatureError,
            "feature is disabled: " + feature,
            span
        );
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

    private sealed interface Expression
        extends SyntaxNode
        permits Literal, Reference, ListExpression, Unary, Binary, Quantifier, LengthExpression
    {
        LanguageDiagnostic.SourceSpan span();
    }

    private record Literal(Token token) implements Expression {
        @Override
        public LanguageDiagnostic.SourceSpan span() {
            return LanguageCompilerVisitor.span(this.token);
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
        SemanticAst.UnaryOperator operator,
        Expression operand,
        LanguageDiagnostic.SourceSpan span
    ) implements Expression {}

    private record Binary(
        SemanticAst.BinaryOperator operator,
        Expression left,
        Expression right,
        LanguageDiagnostic.SourceSpan span
    ) implements Expression {}

    private record Quantifier(
        SemanticAst.QuantifierOperator operator,
        Expression collection,
        String elementName,
        Expression predicate,
        LanguageDiagnostic.SourceSpan span
    ) implements Expression {}

    private record LengthExpression(Expression operand, LanguageDiagnostic.SourceSpan span) implements Expression {}

    private static final class Scope {

        private final @Nullable Scope parent;
        private final Map<String, SemanticAst.Expression> values = new HashMap<>();

        private Scope(@Nullable Scope parent) {
            this.parent = parent;
        }

        private void put(String name, SemanticAst.Expression value) {
            this.values.put(name, value);
        }

        private SemanticAst.@Nullable Expression lookup(String name) {
            SemanticAst.Expression value = this.values.get(name);
            return value != null ? value : this.parent == null ? null : this.parent.lookup(name);
        }
    }
}
