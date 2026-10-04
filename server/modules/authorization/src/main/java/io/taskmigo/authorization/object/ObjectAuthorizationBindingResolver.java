package io.taskmigo.authorization.object;

import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceSchemaResolver;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaContext;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/// Resolves the Object Authorization binding compatible with the effective semantic schema for one application surface.
@FunctionalInterface
public interface ObjectAuthorizationBindingResolver {

    /// Resolves the exact compatible Object Authorization binding for the supplied application type and schema context.
    ObjectAuthorizationBinding<?> resolve(Class<?> objectType, SchemaContext context);

    /// Creates a resolver backed by registered bindings while delegating semantic schema selection to the supplied resolver.
    static ObjectAuthorizationBindingResolver registered(
        Collection<? extends ObjectAuthorizationBinding<?>> bindings,
        ResourceSchemaResolver schemas
    ) {
        List<ObjectAuthorizationBinding<?>> declared = List.copyOf(bindings);
        Objects.requireNonNull(schemas);
        return (objectType, context) -> {
            List<ObjectAuthorizationBinding<?>> candidates = declared
                .stream()
                .filter(binding -> binding.objectType().equals(objectType))
                .toList();
            if (candidates.isEmpty()) {
                throw new IllegalStateException(
                    "No Object Authorization binding registered for " + objectType.getName()
                );
            }
            List<ResourceType> resourceTypes = candidates
                .stream()
                .map(ObjectAuthorizationBinding::resourceType)
                .distinct()
                .toList();
            if (resourceTypes.size() != 1) {
                throw new IllegalStateException(
                    "multiple resource types registered for Object Authorization " + objectType.getName()
                );
            }
            ResourceSchema schema = schemas.resolve(resourceTypes.getFirst(), context);
            List<ObjectAuthorizationBinding<?>> compatible = candidates
                .stream()
                .filter(binding -> binding.resourceType().equals(schema.type()))
                .filter(binding -> binding.schemaFingerprint().equals(schema.fingerprint()))
                .toList();
            if (compatible.isEmpty()) {
                throw new IllegalStateException(
                    "No Object Authorization binding registered for effective resource schema " + schema.type().value()
                );
            }
            if (compatible.size() != 1) {
                throw new IllegalStateException(
                    "multiple Object Authorization bindings registered for effective resource schema " +
                        schema.type().value()
                );
            }
            return compatible.getFirst();
        };
    }
}
