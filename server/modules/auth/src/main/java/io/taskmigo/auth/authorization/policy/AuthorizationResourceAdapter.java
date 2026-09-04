package io.taskmigo.auth.authorization.policy;

import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/// Resolves one resource type into immutable values suitable for policy evaluation.
public interface AuthorizationResourceAdapter {
    /// Returns the resource type handled by this adapter.
    String type();

    /// Resolves all requested keys in one bounded operation.
    ///
    /// Implementations must return policy-safe maps rather than persistence entities or repository objects.
    ///
    /// @param keys resource keys, deduplicated by the registry
    /// @return values keyed by their canonical resource key
    Map<String, Map<String, ?>> resolve(@NonNull Collection<String> keys);
}
