package io.taskmigo.language;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Binds Language root names to authoritative semantic resource schemas for one compilation.
public final class CompilerEnvironment {
    private final Map<String, Root> roots;
    private final DependencyCatalog dependencies;
    private final Map<ResourceType, SchemaFingerprint> schemaFingerprints;
    private final String fingerprint;

    private CompilerEnvironment(Map<String, Root> roots) {
        if (roots.isEmpty()) {
            throw new IllegalArgumentException("compiler environment requires at least one root");
        }
        HashMap<String, Root> validated = new HashMap<>();
        LinkedHashMap<ResourceType, SchemaFingerprint> fingerprints = new LinkedHashMap<>();
        roots.forEach((name, root) -> {
            Objects.requireNonNull(name);
            Objects.requireNonNull(root);
            if (name.isBlank()) {
                throw new IllegalArgumentException("compiler root name must not be blank");
            }
            validated.put(name, root);
            SchemaFingerprint previous = fingerprints.putIfAbsent(root.schema().type(), root.schema().fingerprint());
            if (previous != null && !previous.equals(root.schema().fingerprint())) {
                throw new IllegalArgumentException(
                    "resource type has conflicting schema fingerprints: " + root.schema().type().value()
                );
            }
        });
        this.roots = Map.copyOf(validated);
        this.dependencies = new DependencyCatalog(this.roots.keySet());
        this.schemaFingerprints = Map.copyOf(fingerprints);
        StringBuilder canonical = new StringBuilder();
        this.roots
            .keySet()
            .stream()
            .sorted()
            .forEach(name -> {
                Root root = Objects.requireNonNull(this.roots.get(name));
                canonical
                    .append('|')
                    .append(name)
                    .append(':')
                    .append(root.schema().type().value())
                    .append(':')
                    .append(root.schema().fingerprint().value())
                    .append(':')
                    .append(root.type())
                    .append(':')
                    .append(root.nullable())
                    .append(':')
                    .append(root.symbolic());
            });
        this.fingerprint = LanguageFingerprint.of(canonical.toString());
    }

    /// Creates immutable root-to-resource bindings for one compilation context.
    public static CompilerEnvironment of(Map<String, Root> roots) {
        return new CompilerEnvironment(roots);
    }

    /// Returns every effective schema fingerprint keyed by semantic resource identity.
    public Map<ResourceType, SchemaFingerprint> schemaFingerprints() {
        return this.schemaFingerprints;
    }

    Root root(String name) {
        Root root = this.roots.get(name);
        if (root == null) {
            throw new IllegalArgumentException("unknown compiler root: " + name);
        }
        return root;
    }

    String fingerprint() {
        return this.fingerprint;
    }

    int rootSlot(String root) {
        return this.dependencies.slot(root);
    }

    int rootCount() {
        return this.dependencies.size();
    }

    Set<String> dependency(String root) {
        return this.dependencies.dependency(root);
    }

    Set<String> noDependencies() {
        return this.dependencies.empty();
    }

    /// Associates one compiler root with its semantic schema and partial-evaluation behavior.
    public record Root(ResourceSchema schema, LanguageType type, boolean nullable, boolean symbolic) {
        public Root(ResourceSchema schema, boolean symbolic) {
            this(
                schema,
                new LanguageType.StructuredType(schema.type().value(), Map.of()),
                false,
                symbolic
            );
        }

        public Root {
            Objects.requireNonNull(schema);
            Objects.requireNonNull(type);
        }
    }
}
