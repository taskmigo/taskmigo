package io.taskmigo.language;

import java.util.Map;

/// Resolves the effective semantic schema without exposing its provider or storage mechanism.
@FunctionalInterface
public interface ResourceSchemaResolver {
    /// Resolves the current schema for a resource type and provider-neutral context.
    ResourceSchema resolve(ResourceType type, SchemaContext context);

    /// Creates a resolver backed by an immutable set of already selected schemas.
    static ResourceSchemaResolver fixed(Map<ResourceType, ResourceSchema> schemas) {
        Map<ResourceType, ResourceSchema> declared = Map.copyOf(schemas);
        return (type, context) -> {
            ResourceSchema schema = declared.get(type);
            if (schema == null) {
                throw new IllegalArgumentException("unknown resource schema: " + type.value());
            }
            return schema;
        };
    }
}
