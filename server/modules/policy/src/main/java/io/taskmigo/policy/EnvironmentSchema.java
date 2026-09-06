package io.taskmigo.policy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/// Defines the typed roots and paths visible to one compiled policy.
@SuppressWarnings("checkstyle:NeedBraces")
public final class EnvironmentSchema {

    private final String identity;
    private final Map<String, Root> roots;

    public EnvironmentSchema(String identity, Map<String, Root> roots) {
        if (identity == null || identity.isBlank() || roots.isEmpty()) throw new IllegalArgumentException(
            "schema requires an identity and roots"
        );
        this.identity = identity;
        this.roots = Map.copyOf(roots);
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
        String joined = String.join(".", path);
        Field exact = root.fields().get(joined);
        if (exact != null) return exact;
        for (int index = path.size() - 1; index >= 0; index--) {
            Field prefix = root.fields().get(String.join(".", path.subList(0, index)));
            if (prefix != null && prefix.dynamicMemberType() != null && index < path.size()) {
                return new Field(prefix.dynamicMemberType(), false, prefix.symbolic(), prefix.queryable());
            }
        }
        return null;
    }

    /// Returns a deterministic fingerprint for the complete schema contract.
    public String fingerprint() {
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

    /// Describes a typed path and its evaluation/query properties.
    public record Field(
        PolicyType type,
        boolean nullable,
        boolean symbolic,
        boolean queryable,
        @Nullable PolicyType dynamicMemberType
    ) {
        public Field(PolicyType type, boolean nullable, boolean symbolic, boolean queryable) {
            this(type, nullable, symbolic, queryable, null);
        }

        public Field {
            Objects.requireNonNull(type);
        }
    }
}
