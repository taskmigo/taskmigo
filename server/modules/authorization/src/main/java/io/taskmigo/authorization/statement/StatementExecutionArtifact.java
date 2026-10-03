package io.taskmigo.authorization.statement;

import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaFingerprint;
import java.util.Map;

/// Holds the executable derivatives of one database-loaded Statement for one authorization operation.
public record StatementExecutionArtifact(
    StatementInfo statement,
    CompiledSource policy,
    StatementTargetPathMatcher pathMatcher,
    Map<ResourceType, Variant> variants
) {
    public StatementExecutionArtifact(
        StatementInfo statement,
        CompiledSource policy,
        StatementTargetPathMatcher pathMatcher
    ) {
        this(statement, policy, pathMatcher, Map.of());
    }

    public StatementExecutionArtifact {
        variants = Map.copyOf(variants);
    }

    /// Selects the compiled policy variant whose resource schema is exactly compatible with the caller.
    public CompiledSource policy(ResourceType resourceType, SchemaFingerprint fingerprint) {
        Variant variant = this.variants.get(resourceType);
        if (variant == null || !variant.fingerprint().equals(fingerprint)) {
            throw new IllegalArgumentException("no compatible Object Authorization policy variant");
        }
        return variant.policy();
    }

    /// Identifies one immutable resource-specific compiled policy.
    public record Variant(SchemaFingerprint fingerprint, CompiledSource policy) {}
    /// Tests the request target using the matcher prepared when the operation snapshot was built.
    public boolean matches(String requestMethod, String requestPath) {
        return (
            (this.statement.target().api().method().equals("*") ||
                this.statement.target().api().method().equals(requestMethod)) &&
            this.pathMatcher.matches(requestPath)
        );
    }
}
