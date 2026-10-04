package io.taskmigo.authorization.statement;

import io.taskmigo.language.CompiledSource;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaFingerprint;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/// Holds the executable derivatives of one database-loaded Statement for one authorization operation.
public record StatementExecutionArtifact(
    StatementInfo statement,
    @Nullable CompiledSource requestPolicy,
    StatementTargetPathMatcher pathMatcher,
    Map<VariantKey, Variant> variants
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

    /// Returns the request-scoped policy and rejects use for Object Statements.
    public CompiledSource policy() {
        if (this.requestPolicy == null) {
            throw new IllegalStateException("Object Statements have resource-specific policy variants");
        }
        return this.requestPolicy;
    }

    /// Selects the compiled policy variant whose resource schema is exactly compatible with the caller.
    public CompiledSource policy(ResourceType resourceType, SchemaFingerprint fingerprint) {
        Variant variant = this.variants.get(new VariantKey(resourceType, fingerprint));
        if (variant == null) {
            throw new IllegalArgumentException("no compatible Object Authorization policy variant");
        }
        return variant.policy();
    }

    /// Identifies one exact resource-schema compilation variant.
    public record VariantKey(ResourceType resourceType, SchemaFingerprint fingerprint) {}

    /// Holds one immutable resource-specific compiled policy.
    public record Variant(CompiledSource policy) {}

    /// Tests the request target using the matcher prepared when the operation snapshot was built.
    public boolean matches(String requestMethod, String requestPath) {
        return (
            (this.statement.target().api().method().equals("*") ||
                this.statement.target().api().method().equals(requestMethod)) &&
            this.pathMatcher.matches(requestPath)
        );
    }
}
