package io.taskmigo.auth.authorization.policy;

import io.taskmigo.auth.authorization.AuthorizationException;
import io.taskmigo.auth.authorization.statement.Scope;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
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
    private static final JavaScriptPolicyEvaluator EVALUATOR = new JavaScriptPolicyEvaluator();
    private final ConcurrentMap<CacheKey, JavaScriptPolicyModule> cache = new ConcurrentHashMap<>();

    /// Compiles and caches a policy for an immutable Statement scope.
    ///
    /// The source is parsed as ECMAScript syntax and never executed.
    ///
    /// @param source the JavaScript module containing one default-exported policy function
    /// @param scope the Statement scope in which the policy will run
    /// @return the immutable, parser-independent policy representation
    /// @throws AuthorizationException when the module or one of its expressions is unsupported
    public PolicyIr compile(String source, Scope scope) {
        return this.compileModule(source, scope).policy();
    }

    /// Compiles and caches a policy module, including its declarative request resources.
    ///
    /// Request resources are descriptors only at activation time. Their keys are evaluated and resolved by the
    /// request authorization service, after the approved request and principal roots are available.
    ///
    /// @param source the JavaScript module containing a default policy and optional named resources export
    /// @param scope the Statement scope in which the policy will run
    /// @return the immutable compiled policy module
    /// @throws AuthorizationException when the module or one of its expressions is unsupported
    public JavaScriptPolicyModule compileModule(String source, Scope scope) {
        CacheKey key = new CacheKey(source, scope);
        return this.cache.computeIfAbsent(key, ignored -> this.compileUncached(source, scope));
    }

    private JavaScriptPolicyModule compileUncached(String source, Scope scope) {
        if (source.length() > MAX_SOURCE_LENGTH) throw invalid("policy is too long");
        ModuleSource module = sanitizeModule(source);
        AstRoot root;
        try {
            var environment = new CompilerEnvirons();
            environment.setLanguageVersion(Context.VERSION_ES6);
            root = new Parser(environment).parse(module.source(), "authorization-policy.js", 1);
        } catch (RuntimeException exception) {
            throw invalid("policy cannot be parsed");
        }
        FunctionNode resourcesFunction = null;
        List<AstNode> policyDeclarations = new ArrayList<>();
        for (AstNode declaration : root.getStatements()) {
            if (declaration instanceof FunctionNode function && function.getName().equals("resources")) {
                if (module.namedResources() != 1 || resourcesFunction != null) throw invalid(
                    "module must contain only one resources export"
                );
                resourcesFunction = function;
            } else {
                policyDeclarations.add(declaration);
            }
        }
        if (module.namedResources() == 1 && resourcesFunction == null) throw invalid(
            "resources export must be a named function"
        );
        if (scope != Scope.REQUEST && module.namedResources() > 0) throw invalid(
            "resources export is only valid for request Statements"
        );
        if (policyDeclarations.size() != 1) throw invalid("module must contain only one default export");
        FunctionNode function = function(policyDeclarations.getFirst(), "default export");
        Map<String, PolicyIr.Expression> roots = parameters(function, true);
        PolicyIr.Expression expression =
            function.getBody() instanceof Block block
                ? this.statements(childStatements(block), new HashMap<>(roots), 0, new Counter())
                : this.expression(function.getBody(), roots, 0, new Counter());
        expression = fold(expression);
        validateBooleanResult(expression);
        if (scope == Scope.OBJECT) validateObjectPolicy(expression);
        List<ResourceDescriptor> resources = resourcesFunction == null ? List.of() : this.resources(resourcesFunction);
        if (scope == Scope.REQUEST) validateObjectReferences(expression, resources);
        return new JavaScriptPolicyModule(new PolicyIr(expression), resources);
    }

    private static FunctionNode function(AstNode declaration, String kind) {
        AstNode expression =
            declaration instanceof ExpressionStatement statement ? statement.getExpression() : declaration;
        if (!(expression instanceof FunctionNode function)) throw invalid(kind + " must be a function");
        if (function.isGenerator() || function.hasRestParameter() || function.getParams().size() > 1) throw invalid(
            "policy function must have zero or one non-rest parameter"
        );
        return function;
    }

    private static Map<String, PolicyIr.Expression> parameters(FunctionNode function, boolean allowObject) {
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
            ) throw invalid("policy parameter must use simple root destructuring");
            String key = propertyName(property.getKey());
            if (
                (!allowObject && key.equals("object")) ||
                !ROOTS.contains(key) ||
                !(property.getValue() instanceof Name value) ||
                !key.equals(value.getIdentifier())
            ) {
                throw invalid("policy parameter contains an unsupported root");
            }
            if (roots.put(key, new PolicyIr.Reference(key, List.of())) != null) throw invalid(
                "policy parameter contains duplicate roots"
            );
        }
        return Map.copyOf(roots);
    }

    private List<ResourceDescriptor> resources(FunctionNode function) {
        Map<String, PolicyIr.Expression> roots = parameters(function, false);
        if (!(function.getBody() instanceof Block block)) throw invalid("resources export must use a function body");
        List<AstNode> statements = childStatements(block);
        if (statements.size() != 1 || !(statements.getFirst() instanceof ReturnStatement result)) throw invalid(
            "resources export must return one object literal"
        );
        if (
            result.getReturnValue() == null || !(result.getReturnValue() instanceof ObjectLiteral object)
        ) throw invalid("resources export must return one object literal");
        List<ResourceDescriptor> descriptors = new ArrayList<>();
        for (AbstractObjectProperty element : object.getElements()) {
            if (
                !(element instanceof ObjectProperty property) ||
                property.isGetterMethod() ||
                property.isSetterMethod() ||
                property.isMethod()
            ) throw invalid("resource declarations must contain simple properties");
            String name = propertyName(property.getKey());
            if (
                !(property.getValue() instanceof FunctionCall call) ||
                !(call.getTarget() instanceof Name intrinsic) ||
                !intrinsic.getIdentifier().equals("resource") ||
                call.getArguments().size() != 2
            ) throw invalid("resource declarations must call resource(type, key)");
            if (
                !(call.getArguments().getFirst() instanceof StringLiteral type) || type.getValue().isBlank()
            ) throw invalid("resource type must be a nonblank string");
            PolicyIr.Expression key = this.expression(call.getArguments().get(1), roots, 0, new Counter());
            if (containsObject(key)) throw invalid("resource keys cannot depend on object resources");
            if (descriptors.stream().anyMatch(existing -> existing.name().equals(name))) throw invalid(
                "resource declarations cannot contain duplicate names"
            );
            descriptors.add(new ResourceDescriptor(name, type.getValue(), key));
            if (descriptors.size() > 16) throw invalid("request selects too many resources");
        }
        return List.copyOf(descriptors);
    }

    private PolicyIr.Expression statements(
        List<AstNode> statements,
        Map<String, PolicyIr.Expression> environment,
        int index,
        Counter counter
    ) {
        if (index >= statements.size()) throw invalid("policy function must return a boolean on every path");
        counter.consume();
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
            PolicyIr.Expression whenFalse =
                conditional.getElsePart() == null
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
        else if (branch instanceof org.mozilla.javascript.ast.Scope scope) branchStatements.addAll(
            childStatements(scope)
        );
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
        if (name.getIdentifier().equals("undefined")) return new PolicyIr.UndefinedValue();
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
            ) throw invalid("object literals must contain simple properties");
            String name = propertyName(property.getKey());
            if (
                values.put(name, this.expression(property.getValue(), environment, depth + 1, counter)) != null
            ) throw invalid("object literals cannot contain duplicate properties");
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
            case PolicyIr.UndefinedValue ignored -> false;
            case PolicyIr.PropertyAccess property -> containsObject(property.target());
            case PolicyIr.ArrayValue array -> array
                .values()
                .stream()
                .anyMatch(JavaScriptPolicyCompiler::containsObject);
            case PolicyIr.ObjectValue object -> object
                .values()
                .values()
                .stream()
                .anyMatch(JavaScriptPolicyCompiler::containsObject);
            case PolicyIr.Binary binary -> containsObject(binary.left()) || containsObject(binary.right());
            case PolicyIr.Unary unary -> containsObject(unary.operand());
            case PolicyIr.Conditional conditional -> containsObject(conditional.condition()) ||
                containsObject(conditional.whenTrue()) ||
                containsObject(conditional.whenFalse());
        };
    }

    private static boolean containsReference(PolicyIr.Expression expression) {
        return switch (expression) {
            case PolicyIr.Literal ignored -> false;
            case PolicyIr.UndefinedValue ignored -> false;
            case PolicyIr.Reference ignored -> true;
            case PolicyIr.PropertyAccess property -> containsReference(property.target());
            case PolicyIr.ArrayValue array -> array.values().stream().anyMatch(
                JavaScriptPolicyCompiler::containsReference
            );
            case PolicyIr.ObjectValue object -> object.values().values().stream().anyMatch(
                JavaScriptPolicyCompiler::containsReference
            );
            case PolicyIr.Binary binary -> containsReference(binary.left()) || containsReference(binary.right());
            case PolicyIr.Unary unary -> containsReference(unary.operand());
            case PolicyIr.Conditional conditional -> containsReference(conditional.condition()) ||
                containsReference(conditional.whenTrue()) ||
                containsReference(conditional.whenFalse());
        };
    }

    private static PolicyIr.Expression fold(PolicyIr.Expression expression) {
        return switch (expression) {
            case PolicyIr.Literal literal -> literal;
            case PolicyIr.UndefinedValue undefined -> undefined;
            case PolicyIr.Reference reference -> reference;
            case PolicyIr.PropertyAccess property -> foldConstant(
                new PolicyIr.PropertyAccess(fold(property.target()), property.property())
            );
            case PolicyIr.ArrayValue array -> new PolicyIr.ArrayValue(
                array.values().stream().map(JavaScriptPolicyCompiler::fold).toList()
            );
            case PolicyIr.ObjectValue object -> new PolicyIr.ObjectValue(
                object.values().entrySet().stream().collect(
                    java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> fold(entry.getValue()),
                        (first, second) -> first,
                        LinkedHashMap::new
                    )
                )
            );
            case PolicyIr.Binary binary -> foldBinary(binary);
            case PolicyIr.Unary unary -> foldConstant(new PolicyIr.Unary(unary.operator(), fold(unary.operand())));
            case PolicyIr.Conditional conditional -> foldConditional(conditional);
        };
    }

    private static PolicyIr.Expression foldBinary(PolicyIr.Binary binary) {
        PolicyIr.Expression left = fold(binary.left());
        if (binary.operator() == PolicyIr.BinaryOperator.AND && left instanceof PolicyIr.Literal literal) {
            return truthy(literal.value()) ? fold(binary.right()) : literal;
        }
        if (binary.operator() == PolicyIr.BinaryOperator.OR && left instanceof PolicyIr.Literal literal) {
            return truthy(literal.value()) ? literal : fold(binary.right());
        }
        return foldConstant(new PolicyIr.Binary(binary.operator(), left, fold(binary.right())));
    }

    private static PolicyIr.Expression foldConditional(PolicyIr.Conditional conditional) {
        PolicyIr.Expression condition = fold(conditional.condition());
        if (condition instanceof PolicyIr.Literal literal) {
            return truthy(literal.value()) ? fold(conditional.whenTrue()) : fold(conditional.whenFalse());
        }
        return foldConstant(
            new PolicyIr.Conditional(condition, fold(conditional.whenTrue()), fold(conditional.whenFalse()))
        );
    }

    private static PolicyIr.Expression foldConstant(PolicyIr.Expression expression) {
        if (containsReference(expression)) return expression;
        @Nullable
        Object value;
        try {
            value = EVALUATOR.evaluateValue(expression, Map.of());
        } catch (RuntimeException ignored) {
            return expression;
        }
        if (value == JavaScriptPolicyEvaluator.undefinedValue()) return new PolicyIr.UndefinedValue();
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return new PolicyIr.Literal(value);
        }
        return expression;
    }

    private static void validateBooleanResult(PolicyIr.Expression expression) {
        if (resultType(expression) != ResultType.BOOLEAN) throw invalid(
            "policy must return a boolean on every reachable path"
        );
    }

    private static ResultType resultType(PolicyIr.Expression expression) {
        return switch (expression) {
            case PolicyIr.Literal literal -> literalType(literal.value());
            case PolicyIr.UndefinedValue ignored -> ResultType.UNDEFINED;
            case PolicyIr.Reference ignored -> ResultType.UNKNOWN;
            case PolicyIr.PropertyAccess property -> {
                ResultType target = resultType(property.target());
                yield property.property().equals("length") &&
                    (target == ResultType.STRING || target == ResultType.ARRAY)
                    ? ResultType.NUMBER
                    : ResultType.UNKNOWN;
            }
            case PolicyIr.ArrayValue ignored -> ResultType.ARRAY;
            case PolicyIr.ObjectValue ignored -> ResultType.OBJECT;
            case PolicyIr.Binary binary -> switch (binary.operator()) {
                case OR, AND ->
                    resultType(binary.left()) == ResultType.BOOLEAN && resultType(binary.right()) == ResultType.BOOLEAN
                        ? ResultType.BOOLEAN
                        : ResultType.UNKNOWN;
                case EQUAL, NOT_EQUAL, GREATER, GREATER_OR_EQUAL, LESS, LESS_OR_EQUAL, IN, CONTAINS, STARTS_WITH,
                    ENDS_WITH ->
                    ResultType.BOOLEAN;
                case ADD ->
                    resultType(binary.left()) == ResultType.STRING || resultType(binary.right()) == ResultType.STRING
                        ? ResultType.STRING
                        : resultType(binary.left()) == ResultType.NUMBER &&
                        resultType(binary.right()) == ResultType.NUMBER
                        ? ResultType.NUMBER
                        : ResultType.UNKNOWN;
                case SUBTRACT, MULTIPLY, DIVIDE, MODULO ->
                    resultType(binary.left()) == ResultType.NUMBER && resultType(binary.right()) == ResultType.NUMBER
                        ? ResultType.NUMBER
                        : ResultType.UNKNOWN;
            };
            case PolicyIr.Unary unary -> unary.operator() == PolicyIr.UnaryOperator.NOT
                ? ResultType.BOOLEAN
                : ResultType.NUMBER;
            case PolicyIr.Conditional conditional ->
                resultType(conditional.whenTrue()) == ResultType.BOOLEAN &&
                    resultType(conditional.whenFalse()) == ResultType.BOOLEAN
                ? ResultType.BOOLEAN
                : ResultType.UNKNOWN;
        };
    }

    private static ResultType literalType(@Nullable Object value) {
        if (value instanceof Boolean) return ResultType.BOOLEAN;
        if (value instanceof Number) return ResultType.NUMBER;
        if (value instanceof String) return ResultType.STRING;
        return value == null ? ResultType.NULL : ResultType.UNKNOWN;
    }

    private static boolean truthy(@Nullable Object value) {
        if (value == null || value == JavaScriptPolicyEvaluator.undefinedValue()) return false;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.doubleValue() != 0 && !Double.isNaN(number.doubleValue());
        return !(value instanceof String string) || !string.isEmpty();
    }

    private static void validateObjectPolicy(PolicyIr.Expression expression) {
        switch (expression) {
            case PolicyIr.Literal ignored -> {
            }
            case PolicyIr.UndefinedValue ignored -> {
            }
            case PolicyIr.Reference reference -> {
                if (reference.root().equals("object") && reference.path().size() != 1) throw invalid(
                    "Object policies may only select direct queryable fields"
                );
            }
            case PolicyIr.PropertyAccess property -> {
                if (containsObject(property)) throw invalid("computed object properties are not queryable");
                validateObjectPolicy(property.target());
            }
            case PolicyIr.ArrayValue array -> {
                if (containsObject(array)) throw invalid("object-dependent collection values are not queryable");
                array.values().forEach(JavaScriptPolicyCompiler::validateObjectPolicy);
            }
            case PolicyIr.ObjectValue object -> {
                if (containsObject(object)) throw invalid("object literals are not queryable");
                throw invalid("object literal values are not queryable");
            }
            case PolicyIr.Binary binary -> {
                if (
                    binary.operator() == PolicyIr.BinaryOperator.AND || binary.operator() == PolicyIr.BinaryOperator.OR
                ) {
                    validateObjectPolicy(binary.left());
                    validateObjectPolicy(binary.right());
                } else if (containsObject(binary.left()) && containsObject(binary.right())) throw invalid(
                    "object-to-object comparisons are not queryable"
                );
                else if (containsObject(binary)) {
                    if (
                        Set.of(
                            PolicyIr.BinaryOperator.ADD,
                            PolicyIr.BinaryOperator.SUBTRACT,
                            PolicyIr.BinaryOperator.MULTIPLY,
                            PolicyIr.BinaryOperator.DIVIDE,
                            PolicyIr.BinaryOperator.MODULO
                        ).contains(binary.operator())
                    ) throw invalid("object arithmetic is not queryable");
                    validateObjectPolicy(binary.left());
                    validateObjectPolicy(binary.right());
                }
            }
            case PolicyIr.Unary unary -> {
                if (unary.operator() != PolicyIr.UnaryOperator.NOT && containsObject(unary)) throw invalid(
                    "object arithmetic is not queryable"
                );
                validateObjectPolicy(unary.operand());
            }
            case PolicyIr.Conditional conditional -> {
                if (containsObject(conditional)) throw invalid("object conditionals are not queryable");
            }
        }
    }

    private static void validateObjectReferences(PolicyIr.Expression expression, List<ResourceDescriptor> resources) {
        Set<String> names = resources
            .stream()
            .map(ResourceDescriptor::name)
            .collect(java.util.stream.Collectors.toSet());
        switch (expression) {
            case PolicyIr.Reference reference -> {
                if (
                    reference.root().equals("object") &&
                    (reference.path().isEmpty() || !names.contains(reference.path().getFirst()))
                ) throw invalid("object references must select a declared request resource");
            }
            case PolicyIr.Literal ignored -> {
            }
            case PolicyIr.UndefinedValue ignored -> {
            }
            case PolicyIr.PropertyAccess property -> validateObjectReferences(property.target(), resources);
            case PolicyIr.ArrayValue array -> array
                .values()
                .forEach(value -> validateObjectReferences(value, resources));
            case PolicyIr.ObjectValue object -> object
                .values()
                .values()
                .forEach(value -> validateObjectReferences(value, resources));
            case PolicyIr.Binary binary -> {
                validateObjectReferences(binary.left(), resources);
                validateObjectReferences(binary.right(), resources);
            }
            case PolicyIr.Unary unary -> validateObjectReferences(unary.operand(), resources);
            case PolicyIr.Conditional conditional -> {
                validateObjectReferences(conditional.condition(), resources);
                validateObjectReferences(conditional.whenTrue(), resources);
                validateObjectReferences(conditional.whenFalse(), resources);
            }
        }
    }

    private static ModuleSource sanitizeModule(String source) {
        StringBuilder sanitized = new StringBuilder(source.length());
        int defaults = 0;
        int namedResources = 0;
        for (int index = 0; index < source.length(); ) {
            char current = source.charAt(index);
            if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '/') {
                int end = source.indexOf('\n', index + 2);
                end = end < 0 ? source.length() : end + 1;
                sanitized.append(source, index, end);
                index = end;
            } else if (current == '/' && index + 1 < source.length() && source.charAt(index + 1) == '*') {
                int end = source.indexOf("*/", index + 2);
                if (end < 0) throw invalid("module contains an unterminated comment");
                end += 2;
                sanitized.append(source, index, end);
                index = end;
            } else if (current == '\'' || current == '"' || current == '`') {
                int end = quotedEnd(source, index, current);
                sanitized.append(source, index, end);
                index = end;
            } else if (Character.isJavaIdentifierStart(current)) {
                int end = index + 1;
                while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) end++;
                String identifier = source.substring(index, end);
                if (identifier.equals("export")) {
                    int next = skipTrivia(source, end);
                    if (wordAt(source, next, "default")) {
                        defaults++;
                        sanitized.append(' ');
                        index = next + "default".length();
                    } else if (wordAt(source, next, "function")) {
                        int name = skipTrivia(source, next + "function".length());
                        if (!wordAt(source, name, "resources")) throw invalid(
                            "only a default export and resources export are supported"
                        );
                        namedResources++;
                        index = end;
                    } else {
                        throw invalid("only a default export and resources export are supported");
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
        if (defaults != 1) throw invalid("module must contain one default export");
        if (namedResources > 1) throw invalid("module must contain only one resources export");
        return new ModuleSource(sanitized.toString(), namedResources);
    }

    private static int quotedEnd(String source, int start, char quote) {
        for (int index = start + 1; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '\\') index++;
            else if (current == quote) return index + 1;
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
        while (index < source.length()) {
            if (Character.isWhitespace(source.charAt(index))) index++;
            else if (source.startsWith("//", index)) {
                int newline = source.indexOf('\n', index + 2);
                index = newline < 0 ? source.length() : newline + 1;
            } else if (source.startsWith("/*", index)) {
                int end = source.indexOf("*/", index + 2);
                if (end < 0) throw invalid("module contains an unterminated comment");
                index = end + 2;
            } else break;
        }
        return index;
    }

    private static AuthorizationException invalid(String message) {
        return new AuthorizationException("Invalid JavaScript authorization policy: " + message);
    }

    private record CacheKey(String source, Scope scope) {}

    private record ModuleSource(String source, int namedResources) {}

    private static final class Counter {

        private int nodes;

        private void consume() {
            this.nodes++;
            if (this.nodes > MAX_NODES) throw invalid("policy contains too many nodes");
        }
    }

    private enum ResultType {
        BOOLEAN,
        NUMBER,
        STRING,
        NULL,
        UNDEFINED,
        ARRAY,
        OBJECT,
        UNKNOWN,
    }
}
