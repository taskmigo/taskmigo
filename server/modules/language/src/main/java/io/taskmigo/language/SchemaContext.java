package io.taskmigo.language;

import java.util.Map;

/// Carries provider-specific, persistence-neutral inputs used to resolve an effective resource schema.
public record SchemaContext(Map<String, Object> attributes) {
    public static final SchemaContext EMPTY = new SchemaContext(Map.of());

    public SchemaContext {
        attributes = Map.copyOf(attributes);
    }
}
