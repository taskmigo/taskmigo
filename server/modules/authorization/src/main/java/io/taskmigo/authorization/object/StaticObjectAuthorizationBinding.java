package io.taskmigo.authorization.object;

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

/// Provides an immutable code-backed Object Authorization binding for one semantic resource schema.
public final class StaticObjectAuthorizationBinding<Q> implements ObjectAuthorizationBinding<Q> {

    private final Class<Q> objectType;
    private final ResourceSchema resourceSchema;
    private final Map<FieldId, ObjectAuthorizationFieldBinding> fields;

    /// Creates a binding after verifying every execution field belongs to the supplied semantic schema.
    public StaticObjectAuthorizationBinding(
        Class<Q> objectType,
        ResourceSchema resourceSchema,
        Collection<ObjectAuthorizationFieldBinding> fields
    ) {
        this.objectType = Objects.requireNonNull(objectType);
        this.resourceSchema = Objects.requireNonNull(resourceSchema);
        List<ObjectAuthorizationFieldBinding> declared = List.copyOf(fields);
        declared.forEach(field ->
            resourceSchema
                .fields()
                .stream()
                .filter(semantic -> semantic.id().equals(field.id()))
                .findFirst()
                .orElseThrow(() ->
                    new IllegalArgumentException("binding field is not declared by schema: " + field.id())
                )
        );
        this.fields = declared.stream().collect(
            Collectors.toUnmodifiableMap(ObjectAuthorizationFieldBinding::id, Function.identity(), (left, right) -> {
                throw new IllegalArgumentException("duplicate object binding field: " + left.id());
            })
        );
    }

    @Override
    public Class<Q> objectType() {
        return this.objectType;
    }

    @Override
    public ResourceSchema resourceSchema() {
        return this.resourceSchema;
    }

    @Override
    public Optional<ObjectAuthorizationFieldBinding> field(FieldId id) {
        return Optional.ofNullable(this.fields.get(id));
    }

    @Override
    public Collection<ObjectAuthorizationFieldBinding> fields() {
        return this.fields.values();
    }

    @Override
    public ResourceType resourceType() {
        return this.resourceSchema.type();
    }

    @Override
    public SchemaFingerprint schemaFingerprint() {
        return this.resourceSchema.fingerprint();
    }
}
