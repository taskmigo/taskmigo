package io.taskmigo.auth.authorization.policy;

import io.taskmigo.auth.authorization.AuthorizationException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/// Coordinates bounded, deduplicated, batched resolution of policy-selected resources.
@Service
public final class AuthorizationResourceRegistry {

    private static final int MAX_RESOURCES = 16;
    private static final int MAX_KEY_LENGTH = 256;
    private final JavaScriptPolicyEvaluator evaluator;
    private final Map<String, AuthorizationResourceAdapter> adapters;

    /// Creates a registry from the resource adapters provided by feature modules.
    public AuthorizationResourceRegistry(
        JavaScriptPolicyEvaluator evaluator,
        List<AuthorizationResourceAdapter> adapters
    ) {
        this.evaluator = evaluator;
        this.adapters = adapters.stream().collect(
            Collectors.toUnmodifiableMap(AuthorizationResourceAdapter::type, Function.identity(), (first, second) -> {
                throw new IllegalStateException("duplicate authorization resource adapter");
            })
        );
    }

    /// Resolves resource descriptors without allowing policy code to access persistence directly.
    ///
    /// @param descriptors descriptors collected from matching request policies
    /// @param roots approved request and principal roots used to compute descriptor keys
    /// @return immutable values and descriptor-to-key mappings
    /// @throws AuthorizationException when a descriptor is invalid or cannot be resolved
    public ResolvedResources resolve(Collection<ResourceDescriptor> descriptors, Map<String, ?> roots) {
        if (descriptors.size() > MAX_RESOURCES) throw invalid("request selects too many resources");

        Map<ResourceDescriptor, ResourceKey> descriptorKeys = new LinkedHashMap<>();
        Map<ResourceKey, ResourceKey> uniqueKeys = new LinkedHashMap<>();
        for (ResourceDescriptor descriptor : descriptors) {
            Object value = this.evaluator.evaluateValue(descriptor.key(), roots);
            if (!(value instanceof String key) || key.isBlank() || key.length() > MAX_KEY_LENGTH) throw invalid(
                "resource keys must be nonblank strings of at most " + MAX_KEY_LENGTH + " characters"
            );
            ResourceKey resourceKey = new ResourceKey(descriptor.type(), key);
            descriptorKeys.put(descriptor, resourceKey);
            uniqueKeys.putIfAbsent(resourceKey, resourceKey);
        }

        Map<ResourceKey, Map<String, ?>> resolved = new HashMap<>();
        Map<String, List<ResourceKey>> byType = uniqueKeys
            .values()
            .stream()
            .collect(Collectors.groupingBy(ResourceKey::type, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<ResourceKey>> entry : byType.entrySet()) {
            AuthorizationResourceAdapter adapter = this.adapters.get(entry.getKey());
            if (adapter == null) throw invalid("no authorization resource adapter for type " + entry.getKey());
            Set<String> keys = entry.getValue().stream().map(ResourceKey::key).collect(Collectors.toUnmodifiableSet());
            Map<String, Map<String, ?>> values = adapter.resolve(keys);
            for (ResourceKey resourceKey : entry.getValue()) {
                Map<String, ?> value = values.get(resourceKey.key());
                if (value == null) throw invalid(
                    "authorization resource " + resourceKey.type() + ":" + resourceKey.key() + " was not found"
                );
                resolved.put(resourceKey, Map.copyOf(value));
            }
        }
        return new ResolvedResources(resolved, descriptorKeys);
    }

    private static AuthorizationException invalid(String message) {
        return new AuthorizationException("Authorization resource resolution failed: " + message);
    }
}
