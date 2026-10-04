package io.taskmigo.query;

import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceSchemaResolver;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaContext;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/// Resolves the execution binding compatible with the effective semantic schema for one application query surface.
@FunctionalInterface
public interface QueryBindingResolver {

    /// Resolves one effective schema and its exact compatible execution binding.
    Resolution resolve(Class<?> queryType, SchemaContext context);

    /// Creates a resolver backed by registered bindings while delegating semantic schema selection to the supplied resolver.
    static QueryBindingResolver registered(
        Collection<? extends QueryBinding<?>> bindings,
        ResourceSchemaResolver schemas
    ) {
        List<QueryBinding<?>> declared = List.copyOf(bindings);
        Objects.requireNonNull(schemas);
        return (queryType, context) -> {
            List<QueryBinding<?>> candidates = declared
                .stream()
                .filter(binding -> binding.queryType().equals(queryType))
                .toList();
            if (candidates.isEmpty()) {
                throw new IllegalStateException("No query binding registered for " + queryType.getName());
            }
            List<ResourceType> resourceTypes = candidates.stream().map(QueryBinding::resourceType).distinct().toList();
            if (resourceTypes.size() != 1) {
                throw new IllegalStateException("multiple resource types registered for query " + queryType.getName());
            }
            ResourceSchema schema = schemas.resolve(resourceTypes.getFirst(), context);
            List<QueryBinding<?>> compatible = candidates
                .stream()
                .filter(binding -> binding.resourceType().equals(schema.type()))
                .filter(binding -> binding.schemaFingerprint().equals(schema.fingerprint()))
                .toList();
            if (compatible.isEmpty()) {
                throw new IllegalStateException(
                    "No query binding registered for effective resource schema " + schema.type().value()
                );
            }
            if (compatible.size() != 1) {
                throw new IllegalStateException(
                    "multiple query bindings registered for effective resource schema " + schema.type().value()
                );
            }
            return new Resolution(schema, compatible.getFirst());
        };
    }

    /// Carries one immutable semantic-schema and execution-binding snapshot.
    record Resolution(ResourceSchema schema, QueryBinding<?> binding) {
        public Resolution {
            Objects.requireNonNull(schema);
            Objects.requireNonNull(binding);
        }
    }
}
