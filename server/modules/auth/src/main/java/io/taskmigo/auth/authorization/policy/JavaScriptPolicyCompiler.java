package io.taskmigo.auth.authorization.policy;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.statement.Scope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Node;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.AbstractObjectProperty;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.Block;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.FunctionNode;
import org.mozilla.javascript.ast.IfStatement;
import org.mozilla.javascript.ast.InfixExpression;
import org.mozilla.javascript.ast.KeywordLiteral;
import org.mozilla.javascript.ast.Name;
import org.mozilla.javascript.ast.NumberLiteral;
import org.mozilla.javascript.ast.ObjectLiteral;
import org.mozilla.javascript.ast.ObjectProperty;
import org.mozilla.javascript.ast.ParenthesizedExpression;
import org.mozilla.javascript.ast.PropertyGet;
import org.mozilla.javascript.ast.ReturnStatement;
import org.mozilla.javascript.ast.StringLiteral;
import org.mozilla.javascript.ast.UnaryExpression;
import org.mozilla.javascript.ast.VariableDeclaration;
import org.mozilla.javascript.ast.VariableInitializer;
import org.springframework.stereotype.Service;

/// Parses supported ECMAScript policy modules and translates them into immutable policy IR.
@Service
public final class JavaScriptPolicyCompiler {

    private static final int MAX_SOURCE_LENGTH = 16_000;
    private static final int MAX_DEPTH = 40;
    private static final int MAX_NODES = 500;
    private static final Set<String> ROOTS = Set.of("request", "principal", "object");
    private static final JavaScriptPolicyEvaluator EVALUATOR = new JavaScriptPolicyEvaluator();

    /// Compiles a policy for an immutable Statement scope.
    ///
    /// The source is parsed as ECMAScript syntax and never executed.
    ///
    /// @param source the JavaScript module containing one default-exported policy function
    /// @param scope the Statement scope in which the policy will run
    /// @return the immutable, parser-independent policy representation
    /// @throws AuthorizationException when the module or one of its expressions is unsupported
    public PolicyIr compile(String source, Scope scope) {
        return this.compileUncached(source, scope);
    }

    private PolicyIr compileUncached(String source, Scope scope) {
        if (source.length() > MAX_SOURCE_LENGTH) {
            throw invalid("policy is too long");
        }
        String sanitized = sanitizeModule(source);
        AstRoot root;
        try {
            var environment = new CompilerEnvirons();
            environment.setLanguageVersion(Context.VERSION_ES6);
            root = new Parser(environment).parse(sanitized, "authorization-policy.js", 1);
        } catch (RuntimeException exception) {
            throw invalid("policy cannot be parsed");
        }
        if (root.getStatements().size() != 1) {
            throw invalid("module must contain only one default export");
        }
        FunctionNode function = function(root.getStatements().getFirst(), "default export");
        Map<String, PolicyIr.Expression> roots = parameters(function, scope == Scope.OBJECT);
        PolicyIr.Expression expression =
            function.getBody() instanceof Block block
                ? this.statements(childStatements(block), new HashMap<>(roots), 0, new Counter())
                : this.expression(function.getBody(), roots, 0, new Counter());
        expression = fold(expression).expression();
        if (scope == Scope.OBJECT) {
            validateObjectPolicy(expression);
        }
        return new PolicyIr(expression);
    }

    private static FunctionNode function(AstNode declaration, String kind) {
        AstNode expression =
            declaration instanceof ExpressionStatement statement ? statement.getExpression() : declaration;
        if (!(expression instanceof FunctionNode function)) {
            throw invalid(kind + " must be a function");
        }
        if (function.isGenerator() || function.hasRestParameter() || function.getParams().size() > 1) {
            throw invalid("policy function must have zero or one non-rest parameter");
        }
        return function;
    }

    private static Map<String, PolicyIr.Expression> parameters(FunctionNode function, boolean allowObject) {
        if (function.getParams().isEmpty()) {
            return Map.of();
        }
        AstNode parameter = function.getParams().getFirst();
        if (!(parameter instanceof ObjectLiteral object) || object.getElements().isEmpty()) {
            throw invalid("policy parameter must destructure authorization roots");
        }
        Map<String, PolicyIr.Expression> roots = new HashMap<>();
        for (AbstractObjectProperty element : object.getElements()) {
            if (
                !(element instanceof ObjectProperty property) ||
                property.isGetterMethod() ||
                property.isSetterMethod() ||
                property.isMethod()
            ) {
                throw invalid("policy parameter must use simple root destructuring");
            }
            String key = propertyName(property.getKey());
            if (
                (!allowObject && key.equals("object")) ||
                !ROOTS.contains(key) ||
                !(property.getValue() instanceof Name value) ||
                !key.equals(value.getIdentifier())
            ) {
                throw invalid("policy parameter contains an unsupported root");
            }
            if (roots.put(key, new PolicyIr.Reference(key, List.of())) != null) {
                throw invalid("policy parameter contains duplicate roots");
            }
        }
        return Map.copyOf(roots);
    }

    private PolicyIr.Expression statements(
        List<AstNode> statements,
        Map<String, PolicyIr.Expression> environment,
        int index,
        Counter counter
    ) {
        return this.statements(new StatementSequence(statements, null), environment, index, counter);
    }

    private PolicyIr.Expression statements(
        StatementSequence sequence,
        Map<String, PolicyIr.Expression> environment,
        int index,
        Counter counter
    ) {
        if (index >= sequence.statements().size()) {
            return sequence.continuation() == null
                ? new PolicyIr.UndefinedValue()
                : this.statements(sequence.continuation(), environment, 0, counter);
        }
        counter.consume();
        AstNode statement = sequence.statements().get(index);
        switch (statement) {
            case VariableDeclaration declaration -> {
                if (!declaration.isConst()) {
                    throw invalid("only const declarations are supported");
                }
                for (VariableInitializer variable : declaration.getVariables()) {
                    if (!(variable.getTarget() instanceof Name name) || variable.getInitializer() == null) {
                        throw invalid("const declarations must initialize simple names");
                    }
                    if (ROOTS.contains(name.getIdentifier())) {
                        throw invalid("authorization roots cannot be shadowed");
                    }
                    environment.put(
                        name.getIdentifier(),
                        this.expression(variable.getInitializer(), environment, 0, counter)
                    );
                }
                return this.statements(sequence, environment, index + 1, counter);
            }
            case ReturnStatement result -> {
                if (result.getReturnValue() == null) {
                    throw invalid("return must contain a value");
                }
                return this.expression(result.getReturnValue(), environment, 0, counter);
            }
            case IfStatement conditional -> {
                PolicyIr.Expression condition = this.expression(conditional.getCondition(), environment, 0, counter);
                StatementSequence continuation = new StatementSequence(
                    sequence.statements().subList(index + 1, sequence.statements().size()),
                    sequence.continuation()
                );
                PolicyIr.Expression whenTrue = this.branch(
                    conditional.getThenPart(),
                    continuation,
                    environment,
                    counter
                );
                PolicyIr.Expression whenFalse =
                    conditional.getElsePart() == null
                        ? this.statements(continuation, new HashMap<>(environment), 0, counter)
                        : this.branch(conditional.getElsePart(), continuation, environment, counter);
                return new PolicyIr.Conditional(condition, whenTrue, whenFalse);
            }
            default -> throw invalid("unsupported policy statement: " + statement.getClass().getSimpleName());
        }
    }

    private PolicyIr.Expression branch(
        AstNode branch,
        StatementSequence continuation,
        Map<String, PolicyIr.Expression> environment,
        Counter counter
    ) {
        Map<String, PolicyIr.Expression> branchEnvironment = new HashMap<>(environment);
        if (branch instanceof Block block) {
            return this.statements(
                new StatementSequence(childStatements(block), continuation),
                branchEnvironment,
                0,
                counter
            );
        } else if (branch instanceof org.mozilla.javascript.ast.Scope scope) {
            return this.statements(
                new StatementSequence(childStatements(scope), continuation),
                branchEnvironment,
                0,
                counter
            );
        } else {
            return this.statements(new StatementSequence(List.of(branch), continuation), branchEnvironment, 0, counter);
        }
    }

    private PolicyIr.Expression expression(
        AstNode node,
        Map<String, PolicyIr.Expression> environment,
        int depth,
        Counter counter
    ) {
        if (depth > MAX_DEPTH) {
            throw invalid("policy nesting is too deep");
        }
        counter.consume();
        return switch (node) {
            case ParenthesizedExpression parenthesized -> this.expression(
                parenthesized.getExpression(),
                environment,
                depth + 1,
                counter
            );
            case Name name -> name(environment, name);
            case StringLiteral string -> new PolicyIr.Literal(string.getValue());
            case NumberLiteral number -> new PolicyIr.Literal(number.getNumber());
            case KeywordLiteral keyword -> literal(keyword);
            case PropertyGet property -> this.property(property, environment, depth, counter);
            case UnaryExpression unary -> new PolicyIr.Unary(
                unary(unary),
                this.expression(unary.getOperand(), environment, depth + 1, counter)
            );
            case InfixExpression infix -> this.binary(infix, environment, depth, counter);
            default -> throw invalid("unsupported policy expression: " + node.getClass().getSimpleName());
        };
    }

    private static PolicyIr.Expression name(Map<String, PolicyIr.Expression> environment, Name name) {
        if (name.getIdentifier().equals("undefined")) {
            return new PolicyIr.UndefinedValue();
        }
        PolicyIr.Expression value = environment.get(name.getIdentifier());
        if (value == null) {
            throw invalid("unknown variable: " + name.getIdentifier());
        }
        return value;
    }

    private PolicyIr.Expression property(
        PropertyGet property,
        Map<String, PolicyIr.Expression> environment,
        int depth,
        Counter counter
    ) {
        PolicyIr.Expression target = this.expression(property.getTarget(), environment, depth + 1, counter);
        String name = property.getProperty().getIdentifier();
        if (target instanceof PolicyIr.Reference reference) {
            return new PolicyIr.Reference(reference.root(), append(reference.path(), name));
        }
        return new PolicyIr.PropertyAccess(target, name);
    }

    private PolicyIr.Expression binary(
        InfixExpression infix,
        Map<String, PolicyIr.Expression> environment,
        int depth,
        Counter counter
    ) {
        PolicyIr.BinaryOperator operator = switch (infix.getOperator()) {
            case Token.OR -> PolicyIr.BinaryOperator.OR;
            case Token.AND -> PolicyIr.BinaryOperator.AND;
            case Token.SHEQ -> PolicyIr.BinaryOperator.EQUAL;
            case Token.SHNE -> PolicyIr.BinaryOperator.NOT_EQUAL;
            case Token.GT -> PolicyIr.BinaryOperator.GREATER;
            case Token.GE -> PolicyIr.BinaryOperator.GREATER_OR_EQUAL;
            case Token.LT -> PolicyIr.BinaryOperator.LESS;
            case Token.LE -> PolicyIr.BinaryOperator.LESS_OR_EQUAL;
            case Token.ADD -> PolicyIr.BinaryOperator.ADD;
            case Token.SUB -> PolicyIr.BinaryOperator.SUBTRACT;
            case Token.MUL -> PolicyIr.BinaryOperator.MULTIPLY;
            case Token.DIV -> PolicyIr.BinaryOperator.DIVIDE;
            case Token.MOD -> PolicyIr.BinaryOperator.MODULO;
            default -> throw invalid("unsupported policy operator");
        };
        return new PolicyIr.Binary(
            operator,
            this.expression(infix.getLeft(), environment, depth + 1, counter),
            this.expression(infix.getRight(), environment, depth + 1, counter)
        );
    }

    private static PolicyIr.UnaryOperator unary(UnaryExpression unary) {
        return switch (unary.getOperator()) {
            case Token.NOT -> PolicyIr.UnaryOperator.NOT;
            case Token.POS -> PolicyIr.UnaryOperator.PLUS;
            case Token.NEG -> PolicyIr.UnaryOperator.MINUS;
            default -> throw invalid("unsupported policy unary operator");
        };
    }

    private static PolicyIr.Expression literal(KeywordLiteral keyword) {
        return switch (keyword.getType()) {
            case Token.TRUE -> new PolicyIr.Literal(true);
            case Token.FALSE -> new PolicyIr.Literal(false);
            case Token.NULL -> new PolicyIr.Literal(null);
            case Token.UNDEFINED -> new PolicyIr.UndefinedValue();
            default -> throw invalid("unsupported policy literal");
        };
    }

    private static String propertyName(AstNode key) {
        return switch (key) {
            case Name name -> name.getIdentifier();
            case StringLiteral string -> string.getValue();
            case NumberLiteral number -> number.getValue();
            default -> throw invalid("property names must be static");
        };
    }

    private static List<String> append(List<String> path, String value) {
        List<String> result = new ArrayList<>(path.size() + 1);
        result.addAll(path);
        result.add(value);
        return result;
    }

    private static List<AstNode> childStatements(Block block) {
        return childStatements((AstNode) block);
    }

    private static List<AstNode> childStatements(org.mozilla.javascript.ast.Scope block) {
        return childStatements((AstNode) block);
    }

    private static List<AstNode> childStatements(AstNode block) {
        List<AstNode> statements = new ArrayList<>();
        for (Node child : block) {
            statements.add((AstNode) child);
        }
        return statements;
    }

    private static boolean containsObject(PolicyIr.Expression expression) {
        return switch (expression) {
            case PolicyIr.Reference reference -> reference.root().equals("object");
            case PolicyIr.Literal ignored -> false;
            case PolicyIr.UndefinedValue ignored -> false;
            case PolicyIr.PropertyAccess property -> containsObject(property.target());
            case PolicyIr.Binary binary -> containsObject(binary.left()) || containsObject(binary.right());
            case PolicyIr.Unary unary -> containsObject(unary.operand());
            case PolicyIr.Conditional conditional -> containsObject(conditional.condition()) ||
                containsObject(conditional.whenTrue()) ||
                containsObject(conditional.whenFalse());
        };
    }

    private static FoldedExpression fold(PolicyIr.Expression expression) {
        return switch (expression) {
            case PolicyIr.Literal literal -> new FoldedExpression(literal, false);
            case PolicyIr.UndefinedValue undefined -> new FoldedExpression(undefined, false);
            case PolicyIr.Reference reference -> new FoldedExpression(reference, true);
            case PolicyIr.PropertyAccess property -> {
                FoldedExpression target = fold(property.target());
                PolicyIr.Expression folded = foldConstant(
                    new PolicyIr.PropertyAccess(target.expression(), property.property()),
                    target.containsReference()
                );
                yield new FoldedExpression(folded, target.containsReference());
            }
            case PolicyIr.Binary binary -> foldBinary(binary);
            case PolicyIr.Unary unary -> {
                FoldedExpression operand = fold(unary.operand());
                yield new FoldedExpression(
                    foldConstant(
                        new PolicyIr.Unary(unary.operator(), operand.expression()),
                        operand.containsReference()
                    ),
                    operand.containsReference()
                );
            }
            case PolicyIr.Conditional conditional -> foldConditional(conditional);
        };
    }

    private static FoldedExpression foldBinary(PolicyIr.Binary binary) {
        FoldedExpression left = fold(binary.left());
        if (binary.operator() == PolicyIr.BinaryOperator.AND && left.expression() instanceof PolicyIr.Literal literal) {
            return truthy(literal.value()) ? fold(binary.right()) : left;
        }
        if (binary.operator() == PolicyIr.BinaryOperator.OR && left.expression() instanceof PolicyIr.Literal literal) {
            return truthy(literal.value()) ? left : fold(binary.right());
        }
        FoldedExpression right = fold(binary.right());
        boolean containsReference = left.containsReference() || right.containsReference();
        return new FoldedExpression(
            foldConstant(
                new PolicyIr.Binary(binary.operator(), left.expression(), right.expression()),
                containsReference
            ),
            containsReference
        );
    }

    private static FoldedExpression foldConditional(PolicyIr.Conditional conditional) {
        FoldedExpression condition = fold(conditional.condition());
        if (condition.expression() instanceof PolicyIr.Literal literal) {
            return truthy(literal.value()) ? fold(conditional.whenTrue()) : fold(conditional.whenFalse());
        }
        FoldedExpression whenTrue = fold(conditional.whenTrue());
        FoldedExpression whenFalse = fold(conditional.whenFalse());
        boolean containsReference =
            condition.containsReference() || whenTrue.containsReference() || whenFalse.containsReference();
        return new FoldedExpression(
            foldConstant(
                new PolicyIr.Conditional(condition.expression(), whenTrue.expression(), whenFalse.expression()),
                containsReference
            ),
            containsReference
        );
    }

    private static PolicyIr.Expression foldConstant(PolicyIr.Expression expression, boolean containsReference) {
        if (containsReference) {
            return expression;
        }
        Object value;
        try {
            value = EVALUATOR.evaluateValue(expression, Map.of());
        } catch (RuntimeException ignored) {
            return expression;
        }
        if (value == JavaScriptPolicyEvaluator.undefinedValue()) {
            return new PolicyIr.UndefinedValue();
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return new PolicyIr.Literal(value);
        }
        return expression;
    }

    private static boolean truthy(@Nullable Object value) {
        if (value == null || value == JavaScriptPolicyEvaluator.undefinedValue()) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0 && !Double.isNaN(number.doubleValue());
        }
        return !(value instanceof String string) || !string.isEmpty();
    }

    private static void validateObjectPolicy(PolicyIr.Expression expression) {
        switch (expression) {
            case PolicyIr.Literal ignored -> {
            }
            case PolicyIr.UndefinedValue ignored -> {
            }
            case PolicyIr.Reference reference -> {
                if (reference.root().equals("object") && reference.path().size() != 1) {
                    throw invalid("Object policies may only select direct queryable fields");
                }
            }
            case PolicyIr.PropertyAccess property -> {
                if (containsObject(property)) {
                    throw invalid("computed object properties are not queryable");
                }
                validateObjectPolicy(property.target());
            }
            case PolicyIr.Binary binary -> {
                if (binary.operator() == PolicyIr.BinaryOperator.MODULO) {
                    throw invalid("modulo is not queryable for Object authorization");
                }
                if (
                    binary.operator() == PolicyIr.BinaryOperator.AND || binary.operator() == PolicyIr.BinaryOperator.OR
                ) {
                    validateObjectPolicy(binary.left());
                    validateObjectPolicy(binary.right());
                } else if (containsObject(binary.left()) && containsObject(binary.right())) {
                    throw invalid("object-to-object comparisons are not queryable");
                } else if (containsObject(binary)) {
                    validateObjectPolicy(binary.left());
                    validateObjectPolicy(binary.right());
                }
            }
            case PolicyIr.Unary unary -> {
                if (unary.operator() != PolicyIr.UnaryOperator.NOT && containsObject(unary)) {
                    throw invalid("object arithmetic is not queryable");
                }
                validateObjectPolicy(unary.operand());
            }
            case PolicyIr.Conditional conditional -> {
                validateObjectPolicy(conditional.condition());
                validateObjectPolicy(conditional.whenTrue());
                validateObjectPolicy(conditional.whenFalse());
            }
        }
    }

    private static String sanitizeModule(String source) {
        StringBuilder sanitized = new StringBuilder(source.length());
        int defaults = 0;
        int index = 0;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                int end = source.indexOf('\n', index + 2);
                end = end < 0 ? source.length() : end + 1;
                sanitized.append(source, index, end);
                index = end;
            } else if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '*') {
                int end = source.indexOf("*/", index + 2);
                if (end < 0) {
                    throw invalid("module contains an unterminated comment");
                }
                end += 2;
                sanitized.append(source, index, end);
                index = end;
            } else if (current == '\'' || current == '"' || current == '`') {
                int end = quotedEnd(source, index, current);
                sanitized.append(source, index, end);
                index = end;
            } else if (Character.isJavaIdentifierStart(current)) {
                int end = index + 1;
                while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) {
                    end++;
                }
                String identifier = source.substring(index, end);
                if (identifier.equals("export")) {
                    int next = skipTrivia(source, end);
                    if (wordAt(source, next, "default")) {
                        defaults++;
                        sanitized.append(' ');
                        index = next + "default".length();
                    } else {
                        throw invalid("only a default export is supported");
                    }
                } else if (identifier.equals("import")) {
                    throw invalid("imports are not supported");
                } else {
                    sanitized.append(source, index, end);
                    index = end;
                }
            } else {
                sanitized.append(current);
                index++;
            }
        }
        if (defaults != 1) {
            throw invalid("module must contain one default export");
        }
        return sanitized.toString();
    }

    private static int quotedEnd(String source, int start, char quote) {
        int index = start + 1;
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '\\') {
                index += 2;
            } else if (current == quote) {
                return index + 1;
            } else {
                index++;
            }
        }
        throw invalid("module contains an unterminated string");
    }

    private static boolean wordAt(String source, int index, String expected) {
        return (
            source.startsWith(expected, index) &&
            (index + expected.length() == source.length() ||
                !Character.isJavaIdentifierPart(source.charAt(index + expected.length()))) &&
            (index == 0 || !Character.isJavaIdentifierPart(source.charAt(index - 1)))
        );
    }

    private static int skipTrivia(String source, int index) {
        int position = index;
        while (position < source.length()) {
            if (Character.isWhitespace(source.charAt(position))) {
                position++;
            } else if (source.startsWith("//", position)) {
                int newline = source.indexOf('\n', position + 2);
                position = newline < 0 ? source.length() : newline + 1;
            } else if (source.startsWith("/*", position)) {
                int end = source.indexOf("*/", position + 2);
                if (end < 0) {
                    throw invalid("module contains an unterminated comment");
                }
                position = end + 2;
            } else {
                return position;
            }
        }
        return position;
    }

    private static AuthorizationException invalid(String message) {
        return new AuthorizationException("Invalid JavaScript authorization policy: " + message);
    }

    private record StatementSequence(List<AstNode> statements, @Nullable StatementSequence continuation) {}

    private record FoldedExpression(PolicyIr.Expression expression, boolean containsReference) {}

    private static final class Counter {

        private int nodes;

        private void consume() {
            this.nodes++;
            if (this.nodes > MAX_NODES) {
                throw invalid("policy contains too many nodes");
            }
        }
    }
}
