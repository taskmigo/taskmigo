package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.policy.JavaScriptPolicyModule;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// Captures the immutable authorization state used throughout one request or authorization operation.
public record AuthorizationSnapshot(
    UUID userId,
    List<StatementInfo> statements,
    Map<UUID, JavaScriptPolicyModule> compiledPolicies,
    Map<String, ?> roots
) {
    /// Creates a snapshot and compiles its effective Statements once for the operation.
    public AuthorizationSnapshot(UUID userId, List<StatementInfo> statements, Map<String, ?> roots) {
        this(userId, statements, compile(statements), roots);
    }

    /// Creates a snapshot with immutable effective Statements, compiled policies, and authorization input values.
    public AuthorizationSnapshot {
        statements = List.copyOf(statements);
        compiledPolicies = Map.copyOf(compiledPolicies);
        Map<UUID, JavaScriptPolicyModule> policies = compiledPolicies;
        if (
            policies.size() != statements.size() ||
            statements.stream().anyMatch(statement -> !policies.containsKey(statement.id()))
        ) throw new IllegalArgumentException("authorization snapshot must compile every effective Statement");
        roots = immutableMap(roots);
    }

    /// Returns the compiled policy module associated with an effective Statement.
    public JavaScriptPolicyModule compiledPolicy(StatementInfo statement) {
        JavaScriptPolicyModule compiled = this.compiledPolicies.get(statement.id());
        if (compiled == null) throw new IllegalArgumentException("authorization snapshot is missing a compiled policy");
        return compiled;
    }

    private static Map<UUID, JavaScriptPolicyModule> compile(List<StatementInfo> statements) {
        JavaScriptPolicyCompiler compiler = new JavaScriptPolicyCompiler();
        Map<UUID, JavaScriptPolicyModule> compiled = new LinkedHashMap<>();
        for (StatementInfo statement : statements)
            compiled.put(statement.id(), compiler.compileModule(statement.policy(), statement.scope()));
        return compiled;
    }

    private static Map<String, ?> immutableMap(Map<String, ?> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> stringMap = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String name)) throw new IllegalArgumentException(
                    "authorization input map keys must be strings"
                );
                stringMap.put(name, immutableValue(nested));
            });
            return immutableMap(stringMap);
        }
        if (value instanceof List<?> list) return Collections.unmodifiableList(
            new ArrayList<>(list.stream().map(AuthorizationSnapshot::immutableValue).toList())
        );
        if (value instanceof Set<?> set) return Set.copyOf(
            set.stream().map(AuthorizationSnapshot::immutableValue).toList()
        );
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof UUID) {
            return value;
        }
        throw new IllegalArgumentException("authorization input values must be immutable approved values");
    }
}
