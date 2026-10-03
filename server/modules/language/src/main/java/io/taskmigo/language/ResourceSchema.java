package io.taskmigo.language;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/// Defines the authoritative semantic fields of one resource type.
public interface ResourceSchema {
    /// Returns the stable semantic resource identity represented by this schema.
    ResourceType type();

    /// Resolves a source-facing path to its stable semantic field.
    ///
    /// @throws IllegalArgumentException if the path is not declared by this schema
    Field resolve(FieldPath path);

    /// Returns every semantic field declared by this resource.
    Collection<Field> fields();

    /// Returns the collision-resistant identity of the complete effective schema contract.
    SchemaFingerprint fingerprint();

    /// Creates an immutable code-backed or runtime-built schema from semantic field declarations.
    static ResourceSchema of(ResourceType type, Collection<Field> fields) {
        return new ImmutableResourceSchema(type, fields);
    }

    final class ImmutableResourceSchema implements ResourceSchema {

        private final ResourceType type;
        private final Map<FieldPath, Field> fields;
        private final SchemaFingerprint fingerprint;

        private ImmutableResourceSchema(ResourceType type, Collection<Field> fields) {
            this.type = Objects.requireNonNull(type);
            Objects.requireNonNull(fields);
            HashMap<FieldPath, Field> indexed = new HashMap<>();
            HashSet<FieldId> identities = new HashSet<>();
            for (Field field : fields) {
                Objects.requireNonNull(field);
                if (!identities.add(field.id())) {
                    throw new IllegalArgumentException("duplicate field id: " + field.id().value());
                }
                if (indexed.putIfAbsent(field.path(), field) != null) {
                    throw new IllegalArgumentException("duplicate field path: " + field.path().text());
                }
            }
            this.fields = Map.copyOf(indexed);
            this.fingerprint = new SchemaFingerprint(LanguageFingerprint.of(this.canonicalForm()));
        }

        @Override
        public ResourceType type() {
            return this.type;
        }

        @Override
        public Field resolve(FieldPath path) {
            Field field = this.fields.get(Objects.requireNonNull(path));
            if (field == null) {
                throw new IllegalArgumentException(
                    "resource " + this.type.value() + " does not declare field path " + path.text()
                );
            }
            return field;
        }

        @Override
        public Collection<Field> fields() {
            return this.fields.values();
        }

        @Override
        public SchemaFingerprint fingerprint() {
            return this.fingerprint;
        }

        private String canonicalForm() {
            StringBuilder value = new StringBuilder(this.type.value());
            new TreeMap<>(this.fields).forEach((path, field) ->
                value
                    .append('|')
                    .append(field.id().value())
                    .append(':')
                    .append(path.text())
                    .append(':')
                    .append(canonical(field.type()))
                    .append(':')
                    .append(field.nullable())
            );
            return value.toString();
        }

        private static String canonical(LanguageType type) {
            if (type instanceof LanguageType.Scalar scalar) {
                return scalar.name();
            }
            if (type instanceof LanguageType.ListType list) {
                return "LIST<" + canonical(list.elementType()) + ">";
            }
            LanguageType.StructuredType structured = (LanguageType.StructuredType) type;
            StringBuilder value = new StringBuilder("STRUCT<").append(structured.name());
            new TreeMap<>(structured.fields()).forEach((name, field) ->
                value
                    .append('|')
                    .append(name)
                    .append(':')
                    .append(canonical(field.type()))
                    .append(':')
                    .append(field.nullable())
            );
            return value.append('>').toString();
        }
    }
}
