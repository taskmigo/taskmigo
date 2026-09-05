package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.policy.JavaScriptPolicyCompiler;
import io.taskmigo.auth.authorization.policy.PolicyIr;
import io.taskmigo.auth.authorization.statement.StatementExecutionArtifact;
import io.taskmigo.auth.authorization.statement.StatementInfo;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Captures the immutable authorization state used throughout one request or authorization operation.
public record AuthorizationSnapshot(
    UUID userId,
    List<StatementInfo> statements,
    List<StatementExecutionArtifact> executableStatements,
    Map<String, ?> roots
) {
    /// Creates a snapshot and derives executable Statements from the supplied database-loaded rows.
    public AuthorizationSnapshot(UUID userId, List<StatementInfo> statements, Map<String, ?> roots) {
        this(userId, statements, new StatementArtifactFactory(new JavaScriptPolicyCompiler()).build(statements), roots);
    }

    /// Creates a snapshot with immutable executable Statements and authorization input values.
    public AuthorizationSnapshot {
        List<StatementInfo> effectiveStatements = List.copyOf(statements);
        statements = effectiveStatements;
        executableStatements = List.copyOf(executableStatements);
        if (
            executableStatements.size() != statements.size() ||
            executableStatements.stream().anyMatch(artifact -> !effectiveStatements.contains(artifact.statement()))
        ) {
            throw new IllegalArgumentException("authorization snapshot artifacts do not match effective Statements");
        }
        roots = immutableMap(roots);
    }

    /// Returns the compiled Policy IR associated with an effective Statement.
    public PolicyIr compiledPolicy(StatementInfo statement) {
        return this.executableStatements
            .stream()
            .filter(artifact -> artifact.statement().id().equals(statement.id()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("authorization snapshot is missing a compiled policy"))
            .policy();
    }

    private static Map<String, ?> immutableMap(Map<String, ?> values) {
        Map<String, @Nullable Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static @Nullable Object immutableValue(@Nullable Object value) {
        return switch (value) {
            case null -> null;
            case Map<?, ?> map -> {
                Map<String, @Nullable Object> stringMap = new LinkedHashMap<>();
                map.forEach((key, nested) -> {
                    if (!(key instanceof String name)) {
                        throw new IllegalArgumentException("authorization input map keys must be strings");
                    }
                    stringMap.put(name, immutableValue(nested));
                });
                yield immutableMap(stringMap);
            }
            case List<?> list -> list.stream().map(AuthorizationSnapshot::immutableValue).toList();
            case Set<?> set -> Set.copyOf(set.stream().map(AuthorizationSnapshot::immutableValue).toList());
            case String string -> string;
            case Number number -> number;
            case Boolean bool -> bool;
            case UUID uuid -> uuid;
            default -> throw new IllegalArgumentException(
                "authorization input values must be immutable approved values"
            );
        };
    }
}
