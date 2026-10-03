package io.taskmigo.language;

/// Resolves the effective semantic schema without exposing its provider or storage mechanism.
@FunctionalInterface
public interface ResourceSchemaResolver {
    /// Resolves the current schema for a resource type and provider-neutral context.
    ResourceSchema resolve(ResourceType type, SchemaContext context);
}
