package io.taskmigo.query;

import io.taskmigo.language.FieldId;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaFingerprint;
import java.util.Collection;
import java.util.Optional;

/// Translates semantic field identities into one query execution model.
public interface QueryBinding<Q> {
    /// Returns the application contract type used only for generic integration.
    Class<Q> queryType();

    /// Returns the semantic resource type accepted by this binding.
    ResourceType resourceType();

    /// Returns the exact semantic schema fingerprint accepted by this binding.
    SchemaFingerprint schemaFingerprint();

    /// Resolves one semantic field identity to its execution binding.
    @SuppressWarnings("NullableProblems")
    Optional<QueryFieldBinding> field(FieldId id);

    /// Returns every semantic field execution binding.
    Collection<QueryFieldBinding> fields();

    /// Returns the stable compatibility identity carried by opaque query predicates.
    default String identity() {
        return this.resourceType().value() + ':' + this.schemaFingerprint().value();
    }
}
