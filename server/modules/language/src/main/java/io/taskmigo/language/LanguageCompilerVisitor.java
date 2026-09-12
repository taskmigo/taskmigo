package io.taskmigo.language;

import io.taskmigo.language.antlr.EmbeddedLanguageParser;
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
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;

/// Compiles the generated ANTLR parse tree directly into typed Language Semantic AST.
@SuppressWarnings({
    "checkstyle:NeedBraces",
    "checkstyle:OverloadMethodsDeclarationOrder",
    "checkstyle:UnnecessaryFullyQualifiedType",
    "ConstantValue",
})
final class LanguageCompilerVisitor {

    private final EnvironmentSchema schema;
    private final CompilerLimits limits;
    private final CompilationProfile profile;
    private final List<Scope> scopes = new ArrayList<>();
    private final Set<String> requiredRoots = new HashSet<>();
    private int nodes;
    private int syntaxNesting;
    private int quantifierNesting;
    private int lambdaNesting;
    private int localSlotCount;

    LanguageCompilerVisitor(EnvironmentSchema schema, CompilerLimits limits, CompilationProfile profile) {
        this.schema = schema;
        this.limits = limits;
        this.profile = profile;
    }

    SemanticAst.Expression compile(EmbeddedLanguageParser.ProgramContext context) {
        return this.sequence(context.statement(), 0, new Scope(null), 0, null, new HashSet<>());
    }

    SemanticAst.Expression compile(EmbeddedLanguageParser.ExpressionSourceContext context) {
        this.scopes.add(new Scope(null));
        try {
            return this.expression(context.expression());
        } finally {
            this.scopes.removeLast();
        }
    }

    int localSlotCount() {
        return this.localSlotCount;
    }

    Set<String> requiredRoots() {
        return Set.copyOf(this.requiredRoots);
    }

    private SemanticAst.Expression sequence(
        List<EmbeddedLanguageParser.StatementContext> statements,
        int start,
        Scope environment,
        int blockDepth,
        SemanticAst.@Nullable Expression continuation,
        Set<String> declared
    ) {
        if (blockDepth > this.limits.maxBlockDepth()) throw failure(
            LanguageDiagnostic.Category.ComplexityError,
            "program block depth exceeds the limit",
            unknown()
        );
        this.scopes.add(environment);
        try {
            for (int index = start; index < statements.size(); index++) {
                EmbeddedLanguageParser.StatementContext statement = statements.get(index);
                if (statement.constDecl() != null) {
                    EmbeddedLanguageParser.ConstDeclContext constant = statement.constDecl();
                    this.requireFeature(CompilationFeature.LOCAL_BINDINGS, constant.getStart());
                    String name = constant.IDENT().getText();
                    if (!declared.add(name)) throw failure(
                        LanguageDiagnostic.Category.BindingError,
                        "local binding is already declared: " + name,
                        span(constant)
                    );
                    environment.put(name, this.expression(constant.expression()));
                } else if (statement.returnStatement() != null) {
                    return this.expression(statement.returnStatement().expression());
                } else {
                    EmbeddedLanguageParser.IfStatementContext conditional = statement.ifStatement();
                    this.requireFeature(CompilationFeature.CONDITIONAL_CONTROL_FLOW, conditional.getStart());
                    boolean hasElse = conditional.ELSE() != null;
                    boolean hasFollowing = index + 1 < statements.size();
                    SemanticAst.Expression rest =
                        hasFollowing || !hasElse
                            ? this.sequence(
                                  statements,
                                  index + 1,
                                  new Scope(environment),
                                  blockDepth,
                                  continuation,
                                  new HashSet<>(declared)
                              )
                            : continuation;
                    return this.conditional(conditional, environment, blockDepth, rest);
                }
            }
            if (continuation == null) throw failure(
                LanguageDiagnostic.Category.ControlFlowError,
                "program must return a value on every path",
                unknown()
            );
            return continuation;
        } finally {
            this.scopes.removeLast();
        }
    }

    private SemanticAst.Expression conditional(
        EmbeddedLanguageParser.IfStatementContext conditional,
        Scope environment,
        int blockDepth,
        SemanticAst.@Nullable Expression continuation
    ) {
        EmbeddedLanguageParser.BlockContext trueBlock = conditional.block(0);
        SemanticAst.Expression whenTrue = this.sequence(
            trueBlock.statement(),
            0,
            new Scope(environment),
            blockDepth + 1,
            continuation,
            new HashSet<>()
        );
        SemanticAst.Expression whenFalse;
        if (conditional.ELSE() == null) {
            whenFalse = requireExpression(continuation);
        } else if (conditional.ifStatement() != null) {
            this.requireFeature(CompilationFeature.CONDITIONAL_CONTROL_FLOW, conditional.ifStatement().getStart());
            whenFalse = this.conditional(conditional.ifStatement(), environment, blockDepth + 1, continuation);
        } else {
            EmbeddedLanguageParser.BlockContext falseBlock = conditional.block(1);
            whenFalse = this.sequence(
                falseBlock.statement(),
                0,
                new Scope(environment),
                blockDepth + 1,
                continuation,
                new HashSet<>()
            );
        }
        SemanticAst.Expression condition = this.expression(conditional.expression());
        require(condition, LanguageType.Scalar.BOOL, "if condition must be Bool");
        requireMatchingBranches(whenTrue, whenFalse);
        if (
            condition instanceof SemanticAst.Literal literal && literal.value() instanceof Boolean value
        ) return value ? whenTrue : whenFalse;
        return this.node(new SemanticAst.Conditional(condition, whenTrue, whenFalse));
    }

    private SemanticAst.Expression expression(EmbeddedLanguageParser.ExpressionContext context) {
        return this.or(context.orExpression());
    }

    private SemanticAst.Expression or(EmbeddedLanguageParser.OrExpressionContext context) {
        List<EmbeddedLanguageParser.AndExpressionContext> operands = context.andExpression();
        SemanticAst.Expression result = this.and(operands.getFirst());
        for (int index = 1; index < operands.size(); index++) {
            result = this.binary(
                SemanticAst.BinaryOperator.OR,
                result,
                this.and(operands.get(index)),
                sourceSpan(result.span(), span(operands.get(index)))
            );
        }
        return result;
    }

    private SemanticAst.Expression and(EmbeddedLanguageParser.AndExpressionContext context) {
        List<EmbeddedLanguageParser.EqualityExpressionContext> operands = context.equalityExpression();
        SemanticAst.Expression result = this.equality(operands.getFirst());
        for (int index = 1; index < operands.size(); index++) {
            result = this.binary(
                SemanticAst.BinaryOperator.AND,
                result,
                this.equality(operands.get(index)),
                sourceSpan(result.span(), span(operands.get(index)))
            );
        }
        return result;
    }

    private SemanticAst.Expression equality(EmbeddedLanguageParser.EqualityExpressionContext context) {
        List<EmbeddedLanguageParser.ComparisonExpressionContext> operands = context.comparisonExpression();
        SemanticAst.Expression result = this.comparison(operands.getFirst());
        for (int index = 1; index < operands.size(); index++) {
            SemanticAst.BinaryOperator operator = switch (context.getChild(index * 2 - 1).getText()) {
                case "==" -> SemanticAst.BinaryOperator.EQUAL;
                case "!=" -> SemanticAst.BinaryOperator.NOT_EQUAL;
                default -> throw new IllegalStateException("unsupported equality operator");
            };
            SemanticAst.Expression right = this.comparison(operands.get(index));
            result = this.binary(operator, result, right, sourceSpan(result.span(), right.span()));
        }
        return result;
    }

    private SemanticAst.Expression comparison(EmbeddedLanguageParser.ComparisonExpressionContext context) {
        List<EmbeddedLanguageParser.MembershipExpressionContext> operands = context.membershipExpression();
        SemanticAst.Expression result = this.membership(operands.getFirst());
        for (int index = 1; index < operands.size(); index++) {
            SemanticAst.BinaryOperator operator = switch (context.getChild(index * 2 - 1).getText()) {
                case "<" -> SemanticAst.BinaryOperator.LESS;
                case "<=" -> SemanticAst.BinaryOperator.LESS_OR_EQUAL;
                case ">" -> SemanticAst.BinaryOperator.GREATER;
                case ">=" -> SemanticAst.BinaryOperator.GREATER_OR_EQUAL;
                default -> throw new IllegalStateException("unsupported comparison operator");
            };
            SemanticAst.Expression right = this.membership(operands.get(index));
            result = this.binary(operator, result, right, sourceSpan(result.span(), right.span()));
        }
        return result;
    }

    private SemanticAst.Expression membership(EmbeddedLanguageParser.MembershipExpressionContext context) {
        SemanticAst.Expression left = this.additive(context.additiveExpression(0));
        if (context.additiveExpression().size() == 1) return left;
        SemanticAst.Expression right = this.additive(context.additiveExpression(1));
        return this.binary(SemanticAst.BinaryOperator.IN, left, right, sourceSpan(left.span(), right.span()));
    }

    private SemanticAst.Expression additive(EmbeddedLanguageParser.AdditiveExpressionContext context) {
        List<EmbeddedLanguageParser.MultiplicativeExpressionContext> operands = context.multiplicativeExpression();
        SemanticAst.Expression result = this.multiplicative(operands.getFirst());
        for (int index = 1; index < operands.size(); index++) {
            SemanticAst.BinaryOperator operator = switch (context.getChild(index * 2 - 1).getText()) {
                case "+" -> SemanticAst.BinaryOperator.ADD;
                case "-" -> SemanticAst.BinaryOperator.SUBTRACT;
                default -> throw new IllegalStateException("unsupported additive operator");
            };
            SemanticAst.Expression right = this.multiplicative(operands.get(index));
            result = this.binary(operator, result, right, sourceSpan(result.span(), right.span()));
        }
        return result;
    }

    private SemanticAst.Expression multiplicative(EmbeddedLanguageParser.MultiplicativeExpressionContext context) {
        List<EmbeddedLanguageParser.UnaryExpressionContext> operands = context.unaryExpression();
        SemanticAst.Expression result = this.unary(operands.getFirst());
        for (int index = 1; index < operands.size(); index++) {
            SemanticAst.BinaryOperator operator = switch (context.getChild(index * 2 - 1).getText()) {
                case "*" -> SemanticAst.BinaryOperator.MULTIPLY;
                case "/" -> SemanticAst.BinaryOperator.DIVIDE;
                case "%" -> SemanticAst.BinaryOperator.MODULO;
                default -> throw new IllegalStateException("unsupported multiplicative operator");
            };
            SemanticAst.Expression right = this.unary(operands.get(index));
            result = this.binary(operator, result, right, sourceSpan(result.span(), right.span()));
        }
        return result;
    }

    private SemanticAst.Expression unary(EmbeddedLanguageParser.UnaryExpressionContext context) {
        this.syntaxNesting++;
        if (this.syntaxNesting > this.limits.maxSyntaxDepth()) throw failure(
            LanguageDiagnostic.Category.ComplexityError,
            "program syntax depth exceeds the limit",
            span(context)
        );
        try {
            if (context.primary() != null) return this.primary(context.primary());
            SemanticAst.UnaryOperator operator = switch (context.getChild(0).getText()) {
                case "!" -> SemanticAst.UnaryOperator.NOT;
                case "+" -> SemanticAst.UnaryOperator.PLUS;
                case "-" -> SemanticAst.UnaryOperator.MINUS;
                default -> throw new IllegalStateException("unsupported unary operator");
            };
            SemanticAst.Expression operand = this.unary(context.unaryExpression());
            this.requireFeature(
                operator == SemanticAst.UnaryOperator.NOT
                    ? CompilationFeature.LOGICAL_OPERATORS
                    : CompilationFeature.ARITHMETIC_OPERATORS,
                span(context)
            );
            require(
                operand,
                operator == SemanticAst.UnaryOperator.NOT ? LanguageType.Scalar.BOOL : LanguageType.Scalar.NUMBER,
                "unary operator has an incompatible operand"
            );
            if (operand instanceof SemanticAst.Literal literal) {
                try {
                    return this.literal(EmbeddedLanguageEvaluator.compute(operator, literal.value()), span(context));
                } catch (IllegalArgumentException ignored) {
                    // Runtime failures such as division by zero remain represented in the Semantic AST.
                }
            }
            return this.node(
                new SemanticAst.Unary(
                    operator,
                    operand,
                    operator == SemanticAst.UnaryOperator.NOT
                        ? LanguageType.Scalar.BOOL
                        : LanguageType.Scalar.NUMBER,
                    operand.dependencies(),
                    span(context)
                )
            );
        } finally {
            this.syntaxNesting--;
        }
    }

    private SemanticAst.Expression primary(EmbeddedLanguageParser.PrimaryContext context) {
        if (context.literal() != null) return this.literal(context.literal().getStart());
        if (context.listLiteral() != null) return this.list(context.listLiteral());
        if (context.reference() != null) return this.reference(context.reference());
        if (context.quantifierExpression() != null) return this.quantifier(context.quantifierExpression());
        if (context.lengthExpression() != null) return this.length(context.lengthExpression());
        return this.expression(context.expression());
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
        return this.node(new SemanticAst.Literal(value, typeOf(value), this.schema.noDependencies(), span(token)));
    }

    private SemanticAst.Expression literal(@Nullable Object value, LanguageDiagnostic.SourceSpan span) {
        return this.node(new SemanticAst.Literal(value, typeOf(value), this.schema.noDependencies(), span));
    }

    private SemanticAst.Expression reference(EmbeddedLanguageParser.ReferenceContext context) {
        List<TerminalNode> identifiers = context.IDENT();
        String root = identifiers.getFirst().getText();
        ArrayList<String> path = new ArrayList<>(Math.max(0, identifiers.size() - 1));
        for (int index = 1; index < identifiers.size(); index++) path.add(identifiers.get(index).getText());
        LanguageDiagnostic.SourceSpan sourceSpan = span(context);
        SemanticAst.@Nullable Expression local = this.scopes.getLast().lookup(root);
        if (local != null) {
            if (path.isEmpty()) return local;
            if (local instanceof SemanticAst.Reference localReference && localReference.root().equals("__lambda__")) {
                ArrayList<String> localPath = new ArrayList<>(localReference.path().size() + path.size());
                localPath.addAll(localReference.path());
                localPath.addAll(path);
                return this.node(
                    new SemanticAst.Reference(
                        localReference.root(),
                        localPath,
                        resolveLocalPath(localReference.type(), path),
                        localReference.nullable(),
                        true,
                        -1,
                        localReference.localSlot(),
                        this.schema.noDependencies(),
                        sourceSpan
                    )
                );
            }
        }
        EnvironmentSchema.Field field = this.schema.resolve(root, path);
        if (field == null) throw failure(
            LanguageDiagnostic.Category.BindingError,
            "unknown program reference: " + root + (path.isEmpty() ? "" : "." + String.join(".", path)),
            sourceSpan
        );
        if (!field.symbolic()) this.requiredRoots.add(root);
        return this.node(
            new SemanticAst.Reference(
                root,
                path,
                field.type(),
                field.nullable(),
                field.symbolic(),
                this.schema.rootSlot(root),
                -1,
                this.schema.dependency(root),
                sourceSpan
            )
        );
    }

    private SemanticAst.Expression list(EmbeddedLanguageParser.ListLiteralContext context) {
        this.requireFeature(CompilationFeature.LIST_LITERALS, context.getStart());
        if (context.expression().size() > this.limits.maxListElements()) throw failure(
            LanguageDiagnostic.Category.ComplexityError,
            "list literal exceeds the element limit",
            span(context)
        );
        ArrayList<SemanticAst.Expression> values = new ArrayList<>(context.expression().size());
        for (EmbeddedLanguageParser.ExpressionContext value : context.expression()) values.add(this.expression(value));
        LanguageType element = values.isEmpty() ? LanguageType.Scalar.NULL : values.getFirst().type();
        for (SemanticAst.Expression value : values) {
            if (!value.type().equals(element)) throw failure(
                LanguageDiagnostic.Category.TypeError,
                "list elements must have one homogeneous type",
                value.span()
            );
        }
        return this.node(
            new SemanticAst.ListLiteral(
                values,
                new LanguageType.ListType(element),
                SemanticAst.dependencies(values),
                span(context)
            )
        );
    }

    private SemanticAst.Expression binary(
        SemanticAst.BinaryOperator operator,
        SemanticAst.Expression left,
        SemanticAst.Expression right,
        LanguageDiagnostic.SourceSpan sourceSpan
    ) {
        this.requireFeature(this.feature(operator), sourceSpan);
        LanguageType type = validate(operator, left, right, sourceSpan);
        if (operator == SemanticAst.BinaryOperator.AND && left instanceof SemanticAst.Literal literal) {
            if (literal.value() instanceof Boolean value) return value ? right : literal;
        }
        if (operator == SemanticAst.BinaryOperator.OR && left instanceof SemanticAst.Literal literal) {
            if (literal.value() instanceof Boolean value) return value ? literal : right;
        }
        if (left instanceof SemanticAst.Literal l && right instanceof SemanticAst.Literal r) {
            try {
                return this.literal(EmbeddedLanguageEvaluator.compute(operator, l.value(), r.value()), sourceSpan);
            } catch (IllegalArgumentException ignored) {
                // Runtime failures stay represented rather than becoming compilation failures.
            }
        }
        return this.node(
            new SemanticAst.Binary(operator, left, right, type, SemanticAst.dependencies(left, right), sourceSpan)
        );
    }

    private SemanticAst.Expression quantifier(EmbeddedLanguageParser.QuantifierExpressionContext context) {
        this.requireFeature(CompilationFeature.COLLECTION_QUANTIFIERS, context.getStart());
        this.quantifierNesting++;
        if (this.quantifierNesting > this.limits.maxQuantifierDepth()) throw failure(
            LanguageDiagnostic.Category.ComplexityError,
            "quantifier nesting exceeds the limit",
            span(context)
        );
        SemanticAst.Expression collection = this.expression(context.expression(0));
        if (!(collection.type() instanceof LanguageType.ListType list)) {
            this.quantifierNesting--;
            throw failure(LanguageDiagnostic.Category.TypeError, "quantifier requires a List", span(context));
        }
        int slot = this.lambdaNesting;
        this.lambdaNesting++;
        this.localSlotCount = Math.max(this.localSlotCount, this.lambdaNesting);
        if (this.lambdaNesting > this.limits.maxLambdaDepth()) {
            this.lambdaNesting--;
            this.quantifierNesting--;
            throw failure(LanguageDiagnostic.Category.ComplexityError, "lambda nesting exceeds the limit", span(context));
        }
        String elementName = context.IDENT().getText();
        this.scopes.add(new Scope(this.scopes.isEmpty() ? null : this.scopes.getLast()));
        SemanticAst.Expression predicate;
        try {
            this.scopes
                .getLast()
                .put(
                    elementName,
                    new SemanticAst.Reference(
                        "__lambda__",
                        List.of(elementName),
                        list.elementType(),
                        false,
                        false,
                        -1,
                        slot,
                        this.schema.noDependencies(),
                        span(context)
                    )
                );
            predicate = this.expression(context.expression(1));
        } finally {
            this.scopes.removeLast();
            this.lambdaNesting--;
            this.quantifierNesting--;
        }
        require(predicate, LanguageType.Scalar.BOOL, "quantifier predicate must be Bool");
        SemanticAst.QuantifierOperator operator = switch (context.quantifier().getStart().getType()) {
            case EmbeddedLanguageParser.ALL -> SemanticAst.QuantifierOperator.ALL;
            case EmbeddedLanguageParser.ANY -> SemanticAst.QuantifierOperator.ANY;
            case EmbeddedLanguageParser.NONE -> SemanticAst.QuantifierOperator.NONE;
            default -> throw syntax("invalid quantifier", context.getStart());
        };
        return this.node(
            new SemanticAst.Quantifier(
                operator,
                collection,
                elementName,
                slot,
                predicate,
                LanguageType.Scalar.BOOL,
                SemanticAst.dependencies(collection, predicate),
                span(context)
            )
        );
    }

    private SemanticAst.Expression length(EmbeddedLanguageParser.LengthExpressionContext context) {
        this.requireFeature(CompilationFeature.LENGTH_INTRINSIC, context.getStart());
        SemanticAst.Expression operand = this.expression(context.expression());
        if (
            operand.type() != LanguageType.Scalar.STRING && !(operand.type() instanceof LanguageType.ListType)
        ) throw failure(LanguageDiagnostic.Category.TypeError, "len requires a String or List", span(context));
        if (operand.nullable()) throw failure(
            LanguageDiagnostic.Category.TypeError,
            "len does not accept nullable values",
            span(context)
        );
        if (operand instanceof SemanticAst.Literal literal) {
            if (literal.value() instanceof String text) return this.literal(BigDecimal.valueOf(text.length()), span(context));
            if (literal.value() instanceof List<?> values) return this.literal(BigDecimal.valueOf(values.size()), span(context));
        }
        return this.node(
            new SemanticAst.Length(operand, LanguageType.Scalar.NUMBER, operand.dependencies(), span(context))
        );
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
        throw failure(LanguageDiagnostic.Category.TypeError, "equality requires matching types or nullable values", span);
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

    private SemanticAst.Expression node(SemanticAst.Expression expression) {
        this.nodes++;
        if (this.nodes > this.limits.maxSemanticAstNodes()) throw failure(
            LanguageDiagnostic.Category.ComplexityError,
            "program Semantic AST node count exceeds the limit",
            expression.span()
        );
        return expression;
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
        int index = 1;
        while (index < text.length() - 1) {
            char value = text.charAt(index);
            if (value != '\\') {
                result.append(value);
            } else {
                int escapeIndex = index + 1;
                char escaped = text.charAt(escapeIndex);
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
                        case 'u' -> (char) Integer.parseInt(text, escapeIndex + 1, escapeIndex + 5, 16);
                        default -> throw syntax("invalid string escape", token);
                    }
                );
                index = escaped == 'u' ? escapeIndex + 4 : escapeIndex;
            }
            index++;
        }
        return result.toString();
    }

    private static EmbeddedLanguageException syntax(String message, Token token) {
        return failure(LanguageDiagnostic.Category.SyntaxError, message, span(token));
    }

    private void requireFeature(CompilationFeature feature, Token token) {
        if (!this.profile.enables(feature)) throw failure(
            LanguageDiagnostic.Category.FeatureError,
            "feature is disabled: " + feature,
            span(token)
        );
    }

    private void requireFeature(CompilationFeature feature, LanguageDiagnostic.SourceSpan span) {
        if (!this.profile.enables(feature)) throw failure(
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
