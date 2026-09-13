package io.taskmigo.authorization.request;

import io.taskmigo.authorization.statement.StatementExecutionArtifact;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Captures the immutable authorization state used throughout one request or authorization operation.
record AuthorizationSnapshot(UUID userId, List<StatementExecutionArtifact> executableStatements, Map<String, ?> roots) {
    /// Creates a snapshot with immutable executable Statements and authorization input values.
    public AuthorizationSnapshot {
        executableStatements = List.copyOf(executableStatements);
        roots = immutableMap(roots);
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
            case Set<?> set -> {
                Set<@Nullable Object> copy = new LinkedHashSet<>();
                set.forEach(element -> copy.add(immutableValue(element)));
                yield Collections.unmodifiableSet(copy);
            }
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
