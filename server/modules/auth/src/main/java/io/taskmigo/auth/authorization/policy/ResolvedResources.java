package io.taskmigo.auth.authorization.policy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Holds the bounded resource lookups performed for one authorization decision.
public record ResolvedResources(
    Map<ResourceKey, Map<String, ?>> values,
    Map<ResourceDescriptor, ResourceKey> keys
) {

    /// Creates an immutable resolution result.
    public ResolvedResources {
        values = Map.copyOf(values);
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
            if (key == null || value == null) throw new IllegalStateException("resource resolution is incomplete");
            result.put(descriptor.name(), value);
        }
        return Map.copyOf(result);
    }
}
