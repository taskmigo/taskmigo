package io.taskmigo.auth.authorization.request;

import io.taskmigo.auth.authorization.statement.StatementInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// Captures the immutable authorization state used throughout one request or authorization operation.
public record AuthorizationSnapshot(UUID userId, List<StatementInfo> statements, Map<String, ?> roots) {
    /// Creates a snapshot with immutable effective Statements and authorization input values.
    public AuthorizationSnapshot {
        statements = List.copyOf(statements);
        roots = immutableMap(roots);
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
