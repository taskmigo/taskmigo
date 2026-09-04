package io.taskmigo.auth.authorization.policy;

import java.util.List;

/// Represents a compiled policy module and its declarative request resources.
public record JavaScriptPolicyModule(PolicyIr policy, List<ResourceDescriptor> resources) {
    /// Creates an immutable compiled module.
    public JavaScriptPolicyModule {
        resources = List.copyOf(resources);
    }
}
