package io.taskmigo.auth.authorization.policy;

import io.taskmigo.auth.authorization.condition.AuthorizationException;
import io.taskmigo.auth.authorization.statement.Scope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Node;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.Token;
import org.mozilla.javascript.ast.AbstractObjectProperty;
import org.mozilla.javascript.ast.ArrayLiteral;
import org.mozilla.javascript.ast.AstNode;
import org.mozilla.javascript.ast.AstRoot;
import org.mozilla.javascript.ast.Block;
import org.mozilla.javascript.ast.ConditionalExpression;
import org.mozilla.javascript.ast.ElementGet;
import org.mozilla.javascript.ast.ExpressionStatement;
import org.mozilla.javascript.ast.FunctionCall;
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
    private final ConcurrentMap<CacheKey, PolicyIr> cache = new ConcurrentHashMap<>();

    /// Compiles and caches a policy for an immutable Statement scope.
    ///
    /// The source is parsed as ECMAScript syntax and never executed. Request policies cannot read the `object`
    /// root until request-time resource support is introduced in the next phase.
    ///
    /// @param source the JavaScript module containing one default-exported policy function
    /// @param scope the Statement scope in which the policy will run
    /// @return the immutable, parser-independent policy representation
    /// @throws AuthorizationException when the module or one of its expressions is unsupported
    public PolicyIr compile(String source, Scope scope) {
        CacheKey key = new CacheKey(source, scope);
        return this.cache.computeIfAbsent(key, ignored -> this.compileUncached(source, scope));
    }

    private PolicyIr compileUncached(String source, Scope scope) {
        if (source.length() > MAX_SOURCE_LENGTH) throw invalid("policy is too long");
        String functionSource = defaultExportBody(source);
        AstRoot root;
        try {
            var environment = new CompilerEnvirons();
            environment.setLanguageVersion(Context.VERSION_ES6);
            root = new Parser(environment).parse(functionSource, "authorization-policy.js", 1);
        } catch (RuntimeException exception) {
            throw invalid("policy cannot be parsed");
        }
        if (root.getStatements().size() != 1) throw invalid("module must contain only one default export");
        AstNode declaration = root.getStatements().getFirst();
        FunctionNode function = function(declaration);
        Map<String, PolicyIr.Expression> roots = parameters(function);
        PolicyIr.Expression expression = function.getBody() instanceof Block block
            ? this.statements(childStatements(block), new HashMap<>(roots), 0, new Counter())
            : this.expression(function.getBody(), roots, 0, new Counter());
        if (scope == Scope.REQUEST && containsObject(expression)) throw invalid(
            "object references are only valid for object Statements"
        );
        return new PolicyIr(expression);
    }

    private static FunctionNode function(AstNode declaration) {
        AstNode expression = declaration instanceof ExpressionStatement statement ? statement.getExpression() : declaration;
        if (!(expression instanceof FunctionNode function)) throw invalid("default export must be a function");
        if (function.isGenerator() || function.hasRestParameter() || function.getParams().size() > 1) throw invalid(
            "policy function must have zero or one non-rest parameter"
        );
        return function;
    }

    private static Map<String, PolicyIr.Expression> parameters(FunctionNode function) {
        if (function.getParams().isEmpty()) return Map.of();
        AstNode parameter = function.getParams().getFirst();
        if (!(parameter instanceof ObjectLiteral object) || object.getElements().isEmpty()) throw invalid(
            "policy parameter must destructure authorization roots"
        );
        Map<String, PolicyIr.Expression> roots = new HashMap<>();
        for (AbstractObjectProperty element : object.getElements()) {
            if (
                !(element instanceof ObjectProperty property) ||
                property.isGetterMethod() ||
                property.isSetterMethod() ||
                property.isMethod()
            ) throw invalid(
                "policy parameter must use simple root destructuring"
            );
            String key = propertyName(property.getKey());
            if (!ROOTS.contains(key) || !(property.getValue() instanceof Name value) || !key.equals(value.getIdentifier())) {
                throw invalid("policy parameter contains an unsupported root");
            }
            if (roots.put(key, new PolicyIr.Reference(key, List.of())) != null) throw invalid(
                "policy parameter contains duplicate roots"
            );
        }
        return Map.copyOf(roots);
    }

    private PolicyIr.Expression statements(
        List<AstNode> statements,
        Map<String, PolicyIr.Expression> environment,
        int index,
        Counter counter
    ) {
        if (index >= statements.size()) throw invalid("policy function must return a boolean on every path");
        AstNode statement = statements.get(index);
        if (statement instanceof VariableDeclaration declaration) {
            if (!declaration.isConst()) throw invalid("only const declarations are supported");
            for (VariableInitializer variable : declaration.getVariables()) {
                if (!(variable.getTarget() instanceof Name name) || variable.getInitializer() == null) throw invalid(
                    "const declarations must initialize simple names"
                );
                if (ROOTS.contains(name.getIdentifier())) throw invalid("authorization roots cannot be shadowed");
                environment.put(
                    name.getIdentifier(),
                    this.expression(variable.getInitializer(), environment, 0, counter)
                );
            }
            return this.statements(statements, environment, index + 1, counter);
        }
        if (statement instanceof ReturnStatement result) {
            if (result.getReturnValue() == null) throw invalid("return must contain a value");
            return this.expression(result.getReturnValue(), environment, 0, counter);
        }
        if (statement instanceof IfStatement conditional) {
            PolicyIr.Expression condition = this.expression(conditional.getCondition(), environment, 0, counter);
            List<AstNode> continuation = statements.subList(index + 1, statements.size());
            PolicyIr.Expression whenTrue = this.branch(conditional.getThenPart(), continuation, environment, counter);
            PolicyIr.Expression whenFalse = conditional.getElsePart() == null
                ? this.statements(continuation, new HashMap<>(environment), 0, counter)
                : this.branch(conditional.getElsePart(), continuation, environment, counter);
            return new PolicyIr.Conditional(condition, whenTrue, whenFalse);
        }
        throw invalid("unsupported policy statement: " + statement.getClass().getSimpleName());
    }

    private PolicyIr.Expression branch(
        AstNode branch,
        List<AstNode> continuation,
        Map<String, PolicyIr.Expression> environment,
        Counter counter
    ) {
        List<AstNode> branchStatements = new ArrayList<>();
        if (branch instanceof Block block) branchStatements.addAll(childStatements(block));
        else if (branch instanceof org.mozilla.javascript.ast.Scope scope) branchStatements.addAll(childStatements(scope));
        else branchStatements.add(branch);
        branchStatements.addAll(continuation);
        return this.statements(branchStatements, new HashMap<>(environment), 0, counter);
    }

    private PolicyIr.Expression expression(
        AstNode node,
        Map<String, PolicyIr.Expression> environment,
        int depth,
        Counter counter
    ) {
        if (depth > MAX_DEPTH) throw invalid("policy nesting is too deep");
        counter.nodes++;
        if (counter.nodes > MAX_NODES) throw invalid("policy contains too many nodes");
        return switch (node) {
            case ParenthesizedExpression parenthesized -> this.expression(parenthesized.getExpression(), environment, depth + 1, counter);
            case Name name -> name(environment, name);
            case StringLiteral string -> new PolicyIr.Literal(string.getValue());
            case NumberLiteral number -> new PolicyIr.Literal(number.getNumber());
            case KeywordLiteral keyword -> literal(keyword);
            case PropertyGet property -> this.property(property, environment, depth, counter);
            case ElementGet element -> this.element(element, environment, depth, counter);
            case ArrayLiteral array -> this.array(array, environment, depth, counter);
            case ObjectLiteral object -> this.object(object, environment, depth, counter);
            case FunctionCall call -> this.call(call, environment, depth, counter);
            case ConditionalExpression conditional -> new PolicyIr.Conditional(
                this.expression(conditional.getTestExpression(), environment, depth + 1, counter),
                this.expression(conditional.getTrueExpression(), environment, depth + 1, counter),
                this.expression(conditional.getFalseExpression(), environment, depth + 1, counter)
            );
            case UnaryExpression unary -> new PolicyIr.Unary(
                unary(unary),
                this.expression(unary.getOperand(), environment, depth + 1, counter)
            );
            case InfixExpression infix -> this.binary(infix, environment, depth, counter);
            default -> throw invalid("unsupported policy expression: " + node.getClass().getSimpleName());
        };
    }

    private static PolicyIr.Expression name(Map<String, PolicyIr.Expression> environment, Name name) {
        PolicyIr.Expression value = environment.get(name.getIdentifier());
        if (value == null) throw invalid("unknown variable: " + name.getIdentifier());
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
        if (target instanceof PolicyIr.Reference reference) return new PolicyIr.Reference(
            reference.root(),
            append(reference.path(), name)
        );
        return new PolicyIr.PropertyAccess(target, name);
    }

    private PolicyIr.Expression element(
        ElementGet element,
        Map<String, PolicyIr.Expression> environment,
        int depth,
        Counter counter
    ) {
        String property = switch (element.getElement()) {
            case StringLiteral string -> string.getValue();
            case NumberLiteral number -> number.getValue();
            default -> throw invalid("computed property access must use a static key");
        };
        return new PolicyIr.PropertyAccess(
            this.expression(element.getTarget(), environment, depth + 1, counter),
            property
        );
    }

    private PolicyIr.Expression array(
        ArrayLiteral array,
        Map<String, PolicyIr.Expression> environment,
        int depth,
        Counter counter
    ) {
        List<PolicyIr.Expression> values = new ArrayList<>();
        for (AstNode value : array.getElements()) values.add(this.expression(value, environment, depth + 1, counter));
        return new PolicyIr.ArrayValue(values);
    }

    private PolicyIr.Expression object(
        ObjectLiteral object,
        Map<String, PolicyIr.Expression> environment,
        int depth,
        Counter counter
    ) {
        Map<String, PolicyIr.Expression> values = new LinkedHashMap<>();
        for (AbstractObjectProperty element : object.getElements()) {
            if (
                !(element instanceof ObjectProperty property) ||
                property.isGetterMethod() ||
                property.isSetterMethod() ||
                property.isMethod()
            ) throw invalid(
                "object literals must contain simple properties"
            );
            String name = propertyName(property.getKey());
            if (values.put(name, this.expression(property.getValue(), environment, depth + 1, counter)) != null) throw invalid(
                "object literals cannot contain duplicate properties"
            );
        }
        return new PolicyIr.ObjectValue(values);
    }

    private PolicyIr.Expression call(
        FunctionCall call,
        Map<String, PolicyIr.Expression> environment,
        int depth,
        Counter counter
    ) {
        if (!(call.getTarget() instanceof PropertyGet property)) throw invalid(
            "only selected one-argument string and membership predicates are supported"
        );
        if (call.getArguments().size() != 1) throw invalid("policy predicates require one argument");
        PolicyIr.Expression target = this.expression(property.getTarget(), environment, depth + 1, counter);
        PolicyIr.Expression argument = this.expression(call.getArguments().getFirst(), environment, depth + 1, counter);
        PolicyIr.BinaryOperator operator = switch (property.getProperty().getIdentifier()) {
            case "includes" -> PolicyIr.BinaryOperator.CONTAINS;
            case "startsWith" -> PolicyIr.BinaryOperator.STARTS_WITH;
            case "endsWith" -> PolicyIr.BinaryOperator.ENDS_WITH;
            default -> throw invalid("unsupported policy predicate");
        };
        return new PolicyIr.Binary(operator, target, argument);
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
            case Token.IN -> PolicyIr.BinaryOperator.IN;
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

    private static PolicyIr.Literal literal(KeywordLiteral keyword) {
        return switch (keyword.getType()) {
            case Token.TRUE -> new PolicyIr.Literal(true);
            case Token.FALSE -> new PolicyIr.Literal(false);
            case Token.NULL -> new PolicyIr.Literal(null);
            default -> throw invalid("unsupported policy literal");
        };
    }

    private static String propertyName(AstNode key) {
        if (key instanceof Name name) return name.getIdentifier();
        if (key instanceof StringLiteral string) return string.getValue();
        if (key instanceof NumberLiteral number) return number.getValue();
        throw invalid("property names must be static");
    }

    private static List<String> append(List<String> path, String value) {
        List<String> result = new ArrayList<>(path);
        result.add(value);
        return List.copyOf(result);
    }

    private static List<AstNode> childStatements(Block block) {
        return childStatements((AstNode) block);
    }

    private static List<AstNode> childStatements(org.mozilla.javascript.ast.Scope block) {
        return childStatements((AstNode) block);
    }

    private static List<AstNode> childStatements(AstNode block) {
        List<AstNode> statements = new ArrayList<>();
        for (Node child : block) statements.add((AstNode) child);
        return statements;
    }

    private static boolean containsObject(PolicyIr.Expression expression) {
        return switch (expression) {
            case PolicyIr.Reference reference -> reference.root().equals("object");
            case PolicyIr.Literal ignored -> false;
            case PolicyIr.PropertyAccess property -> containsObject(property.target());
            case PolicyIr.ArrayValue array -> array.values().stream().anyMatch(JavaScriptPolicyCompiler::containsObject);
            case PolicyIr.ObjectValue object -> object.values().values().stream().anyMatch(JavaScriptPolicyCompiler::containsObject);
            case PolicyIr.Binary binary -> containsObject(binary.left()) || containsObject(binary.right());
            case PolicyIr.Unary unary -> containsObject(unary.operand());
            case PolicyIr.Conditional conditional -> containsObject(conditional.condition())
                || containsObject(conditional.whenTrue())
                || containsObject(conditional.whenFalse());
        };
    }

    private static String defaultExportBody(String source) {
        int index = skipTrivia(source, 0);
        index = keyword(source, index, "export");
        index = skipTrivia(source, index);
        index = keyword(source, index, "default");
        return source.substring(skipTrivia(source, index));
    }

    private static int keyword(String source, int index, String expected) {
        if (!source.startsWith(expected, index) || (index + expected.length() < source.length()
            && Character.isJavaIdentifierPart(source.charAt(index + expected.length())))) throw invalid(
            "module must contain one default export"
        );
        return index + expected.length();
    }

    private static int skipTrivia(String source, int index) {
        while (index < source.length()) {
            if (Character.isWhitespace(source.charAt(index))) {
                index++;
            } else if (source.startsWith("//", index)) {
                int newline = source.indexOf('\n', index + 2);
                index = newline < 0 ? source.length() : newline + 1;
            } else if (source.startsWith("/*", index)) {
                int end = source.indexOf("*/", index + 2);
                if (end < 0) throw invalid("module contains an unterminated comment");
                index = end + 2;
            } else {
                break;
            }
        }
        return index;
    }

    private static AuthorizationException invalid(String message) {
        return new AuthorizationException("Invalid JavaScript authorization policy: " + message);
    }

    private record CacheKey(String source, Scope scope) {}

    private static final class Counter {

        private int nodes;
    }
}
