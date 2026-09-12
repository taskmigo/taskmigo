package io.taskmigo.language;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;

/// Defines the typed roots and paths visible to one compiled program.
@SuppressWarnings("checkstyle:NeedBraces")
public final class EnvironmentSchema {

    private final String identity;
    private final Map<String, Root> roots;
    private final Map<String, Map<List<String>, Field>> fieldsByPath;
    private final String fingerprint;

    public EnvironmentSchema(String identity, Map<String, Root> roots) {
        Objects.requireNonNull(identity);
        if (identity.isBlank() || roots.isEmpty()) throw new IllegalArgumentException(
            "schema requires an identity and roots"
        );
        this.identity = identity;
        this.roots = Map.copyOf(roots);
        Map<String, Map<List<String>, Field>> indexedFields = new HashMap<>();
        this.roots.forEach((rootName, root) -> {
            Map<List<String>, Field> fields = new HashMap<>();
            root.fields().forEach((path, field) -> fields.put(List.of(path.split("\\.")), field));
            indexedFields.put(rootName, Map.copyOf(fields));
        });
        this.fieldsByPath = Map.copyOf(indexedFields);
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
        Map<List<String>, Field> indexedFields = Objects.requireNonNull(this.fieldsByPath.get(rootName));
        Field exact = indexedFields.get(path);
        if (exact != null) return exact;
        Optional<Field> current = Optional.ofNullable(root.fields().get(path.getFirst()));
        boolean nullable = current.map(Field::nullable).orElse(false);
        for (int index = 1; index < path.size() && current.isPresent(); index++) {
            Field field = current.get();
            current =
                field.type() instanceof LanguageType.StructuredType structured
                    ? Optional.ofNullable(structured.field(path.get(index)))
                    : Optional.empty();
            nullable = nullable || current.map(Field::nullable).orElse(false);
        }
        if (current.isPresent() && path.size() > 1) {
            Field field = current.get();
            return new Field(field.type(), nullable, field.symbolic(), field.dynamicMemberType());
        }
        for (int index = path.size() - 1; index >= 0; index--) {
            Field prefix = indexedFields.get(path.subList(0, index));
            if (prefix != null && prefix.dynamicMemberType() != null && index < path.size()) {
                return new Field(prefix.dynamicMemberType(), false, prefix.symbolic());
            }
        }
        return null;
    }

    /// Returns a deterministic fingerprint for the complete schema contract.
    public String fingerprint() {
        return this.fingerprint;
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
}
