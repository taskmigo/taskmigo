package io.taskmigo.language;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/// Defines the typed roots and paths visible to one compiled program.
@SuppressWarnings("checkstyle:NeedBraces")
public final class EnvironmentSchema {

    private final String identity;
    private final Map<String, Root> roots;
    private final Map<String, PathNode> paths;
    private final DependencyCatalog dependencies;
    private final String fingerprint;

    public EnvironmentSchema(String identity, Map<String, Root> roots) {
        Objects.requireNonNull(identity);
        if (identity.isBlank() || roots.isEmpty()) throw new IllegalArgumentException(
            "schema requires an identity and roots"
        );
        this.identity = identity;
        this.roots = Map.copyOf(roots);
        this.dependencies = new DependencyCatalog(this.roots.keySet());
        HashMap<String, PathNode> indexed = new HashMap<>();
        this.roots.forEach((rootName, root) -> indexed.put(rootName, PathNode.root(root)));
        this.paths = Map.copyOf(indexed);
        this.fingerprint = this.computeFingerprint();
    }

    /// Returns the consumer-owned schema identity.
    public String identity() {
        return this.identity;
    }

    /// Returns immutable root declarations.
    public Map<String, Root> roots() {
        return this.roots;
    }

    /// Resolves a static path or a member of a declared dynamic map.
    public @Nullable Field resolve(String rootName, List<String> path) {
        Root root = this.roots.get(rootName);
        if (root == null) return null;
        if (path.isEmpty()) return root.value();
        PathNode current = this.paths.get(rootName);
        boolean nullable = false;
        for (String segment : path) {
            if (current == null) return null;
            PathNode child = current.children().get(segment);
            if (child == null) {
                Field currentField = current.field();
                if (currentField != null && currentField.dynamicMemberType() != null) {
                    return new Field(currentField.dynamicMemberType(), false, currentField.symbolic());
                }
                return null;
            }
            current = child;
            if (current.field() != null) nullable = nullable || Objects.requireNonNull(current.field()).nullable();
        }
        Field field = current == null ? null : current.field();
        if (field == null) return null;
        return nullable == field.nullable()
            ? field
            : new Field(field.type(), nullable, field.symbolic(), field.dynamicMemberType());
    }

    /// Returns a deterministic fingerprint for the complete schema contract.
    public String fingerprint() {
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

    private String computeFingerprint() {
        StringBuilder value = new StringBuilder(this.identity);
        this.roots
            .keySet()
            .stream()
            .sorted()
            .forEach(rootName -> {
                Root root = Objects.requireNonNull(this.roots.get(rootName));
                value.append('|').append(rootName).append(root.value());
                new TreeMap<>(root.fields()).forEach((path, field) ->
                    value.append('|').append(path).append(':').append(field)
                );
            });
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /// Describes one schema root and its fields.
    public record Root(Field value, Map<String, Field> fields) {
        public Root {
            Objects.requireNonNull(value);
            fields = Map.copyOf(fields);
        }
    }

    /// Describes one typed path and whether its value may be null or remain symbolic.
    public record Field(
        LanguageType type,
        boolean nullable,
        boolean symbolic,
        @Nullable LanguageType dynamicMemberType
    ) {
        public Field(LanguageType type, boolean nullable, boolean symbolic) {
            this(type, nullable, symbolic, null);
        }

        public Field {
            Objects.requireNonNull(type);
        }
    }

    private record PathNode(@Nullable Field field, Map<String, PathNode> children) {
        private static PathNode root(Root root) {
            MutablePathNode node = new MutablePathNode(null);
            for (Map.Entry<String, Field> entry : root.fields().entrySet()) {
                node.insert(List.of(entry.getKey().split("\\.")), 0, entry.getValue());
            }
            return node.freeze();
        }
    }

    private static final class MutablePathNode {

        private @Nullable Field field;
        private final Map<String, MutablePathNode> children = new HashMap<>();

        private MutablePathNode(@Nullable Field field) {
            this.field = field;
        }

        private void insert(List<String> segments, int index, Field value) {
            if (index == segments.size()) {
                this.field = value;
                this.expand(value);
                return;
            }
            this.children
                .computeIfAbsent(segments.get(index), ignored -> new MutablePathNode(null))
                .insert(segments, index + 1, value);
        }

        private void expand(Field value) {
            if (value.type() instanceof LanguageType.StructuredType structured) {
                for (Map.Entry<String, Field> entry : structured.fields().entrySet()) {
                    this.children
                        .computeIfAbsent(entry.getKey(), ignored -> new MutablePathNode(null))
                        .insert(List.of(), 0, entry.getValue());
                }
            }
        }

        private PathNode freeze() {
            HashMap<String, PathNode> frozen = new HashMap<>();
            this.children.forEach((name, child) -> frozen.put(name, child.freeze()));
            return new PathNode(this.field, Map.copyOf(frozen));
        }
    }
}
