package io.taskmigo.auth.authorization.policy;

/// Describes one persisted resource selected by a request authorization policy.
public record ResourceDescriptor(String name, String type, PolicyIr.Expression key) {
    /// Creates a resource descriptor after validating its stable identifiers.
    public ResourceDescriptor {
        if (name.isBlank() || type.isBlank()) {
            throw new IllegalArgumentException("resource name and type are required");
        }
    }
}
