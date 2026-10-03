package io.taskmigo.query;

import io.taskmigo.language.FieldId;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaFingerprint;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Provides an immutable code-backed execution binding for one semantic resource schema.
public final class StaticQueryBinding<Q> implements QueryBinding<Q> {

    private final Class<Q> queryType;
    private final ResourceType resourceType;
    private final SchemaFingerprint schemaFingerprint;
    private final Map<FieldId, QueryFieldBinding> fields;

    /// Creates a binding after verifying every execution field belongs to the supplied semantic schema.
    public StaticQueryBinding(Class<Q> queryType, ResourceSchema schema, Collection<QueryFieldBinding> fields) {
        this.queryType = Objects.requireNonNull(queryType);
        Objects.requireNonNull(schema);
        this.resourceType = schema.type();
        this.schemaFingerprint = schema.fingerprint();
        List<QueryFieldBinding> declared = List.copyOf(fields);
        declared.forEach(field ->
            schema
                .fields()
                .stream()
                .filter(semantic -> semantic.id().equals(field.id()))
                .findFirst()
                .orElseThrow(() ->
                    new IllegalArgumentException("binding field is not declared by schema: " + field.id())
                )
        );
        this.fields = declared.stream().collect(
            Collectors.toUnmodifiableMap(QueryFieldBinding::id, Function.identity(), (left, right) -> {
                throw new IllegalArgumentException("duplicate query binding field: " + left.id());
            })
        );
    }

    @Override
    public Class<Q> queryType() {
        return this.queryType;
    }

    @Override
    public ResourceType resourceType() {
        return this.resourceType;
    }

    @Override
    public SchemaFingerprint schemaFingerprint() {
        return this.schemaFingerprint;
    }

    @Override
    public Optional<QueryFieldBinding> field(FieldId id) {
        return Optional.ofNullable(this.fields.get(id));
    }

    @Override
    public Collection<QueryFieldBinding> fields() {
        return this.fields.values();
    }
}
