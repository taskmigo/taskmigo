package io.taskmigo.auth.authorization.policy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Holds the bounded resource lookups performed for one authorization decision.
public record ResolvedResources(Map<ResourceKey, Map<String, ?>> values, Map<ResourceDescriptor, ResourceKey> keys) {
    /// Represents the immutable result for a policy that selects no resources.
    public static final ResolvedResources EMPTY = new ResolvedResources(Map.of(), Map.of());

    /// Creates an immutable resolution result.
    public ResolvedResources {
        Map<ResourceKey, Map<String, ?>> immutableValues = new LinkedHashMap<>();
        values.forEach((key, value) -> immutableValues.put(key, immutableMap(value)));
        values = Collections.unmodifiableMap(immutableValues);
        keys = Map.copyOf(keys);
    }

    /// Builds the immutable `object` root for one compiled policy.
    ///
    /// @param descriptors the named resources selected by that policy
    /// @return resource values keyed by policy-visible name
    public Map<String, Map<String, ?>> objectValues(List<ResourceDescriptor> descriptors) {
        Map<String, Map<String, ?>> result = new LinkedHashMap<>();
        for (ResourceDescriptor descriptor : descriptors) {
            ResourceKey key = this.keys.get(descriptor);
            Map<String, ?> value = key == null ? null : this.values.get(key);
            if (key == null || value == null) {
                throw new IllegalStateException("resource resolution is incomplete");
            }
            result.put(descriptor.name(), value);
        }
        return Map.copyOf(result);
    }

    private static Map<String, ?> immutableMap(Map<String, ?> values) {
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((key, value) -> copy.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static @Nullable Object immutableValue(@Nullable Object value) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> {
                if (!(key instanceof String name)) {
                    throw new IllegalArgumentException("authorization resource map keys must be strings");
                }
                copy.put(name, immutableValue(nested));
            });
            return immutableMap(copy);
        }
        if (value instanceof List<?> list) {
            return Collections.unmodifiableList(
                new ArrayList<>(list.stream().map(ResolvedResources::immutableValue).toList())
            );
        }
        if (value instanceof Set<?> set) {
            return Set.copyOf(set.stream().map(ResolvedResources::immutableValue).toList());
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof UUID) {
            return value;
        }
        throw new IllegalArgumentException("authorization resource values must be immutable approved values");
    }
}
