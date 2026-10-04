package io.taskmigo.query;

import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceSchemaResolver;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaContext;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/// Resolves execution bindings by runtime schema context and by exact predicate compatibility identity.
public interface QueryBindingResolver {
    /// Resolves one effective schema and its exact compatible execution binding.
    Resolution resolve(Class<?> queryType, SchemaContext context);

    /// Resolves the exact execution binding retained by an already compiled predicate.
    QueryBinding<?> resolve(Class<?> queryType, String bindingIdentity);

    /// Creates a resolver backed by registered bindings while delegating semantic schema selection to the supplied resolver.
    static QueryBindingResolver registered(
        Collection<? extends QueryBinding<?>> bindings,
        ResourceSchemaResolver schemas
    ) {
        List<QueryBinding<?>> declared = List.copyOf(bindings);
        Objects.requireNonNull(schemas);
        return new QueryBindingResolver() {
            @Override
            public Resolution resolve(Class<?> queryType, SchemaContext context) {
                List<QueryBinding<?>> candidates = candidates(declared, queryType);
                List<ResourceType> resourceTypes = candidates
                    .stream()
                    .map(QueryBinding::resourceType)
                    .distinct()
                    .toList();
                if (resourceTypes.size() != 1) {
                    throw new IllegalStateException(
                        "multiple resource types registered for query " + queryType.getName()
                    );
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
            }

            @Override
            public QueryBinding<?> resolve(Class<?> queryType, String bindingIdentity) {
                Objects.requireNonNull(bindingIdentity);
                List<QueryBinding<?>> compatible = candidates(declared, queryType)
                    .stream()
                    .filter(binding -> binding.identity().equals(bindingIdentity))
                    .toList();
                if (compatible.isEmpty()) {
                    throw new IllegalStateException(
                        "No query binding registered for predicate identity " + bindingIdentity
                    );
                }
                if (compatible.size() != 1) {
                    throw new IllegalStateException(
                        "multiple query bindings registered for predicate identity " + bindingIdentity
                    );
                }
                return compatible.getFirst();
            }
        };
    }

    private static List<QueryBinding<?>> candidates(List<QueryBinding<?>> declared, Class<?> queryType) {
        Objects.requireNonNull(queryType);
        List<QueryBinding<?>> candidates = declared
            .stream()
            .filter(binding -> binding.queryType().equals(queryType))
            .toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No query binding registered for " + queryType.getName());
        }
        return candidates;
    }

    /// Carries one immutable semantic-schema and execution-binding snapshot.
    record Resolution(ResourceSchema schema, QueryBinding<?> binding) {
        public Resolution {
            Objects.requireNonNull(schema);
            Objects.requireNonNull(binding);
        }
    }
}
