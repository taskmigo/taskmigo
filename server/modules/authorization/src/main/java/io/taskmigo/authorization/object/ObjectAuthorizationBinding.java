package io.taskmigo.authorization.object;

import io.taskmigo.language.FieldId;
import io.taskmigo.language.ResourceSchema;
import io.taskmigo.language.ResourceType;
import io.taskmigo.language.SchemaFingerprint;
import java.util.Collection;
import java.util.Optional;

/// Binds a semantic object resource to the trusted execution metadata used by Object Authorization.
public interface ObjectAuthorizationBinding<Q> {

    /// Returns the application object contract represented by this binding.
    Class<Q> objectType();

    /// Returns the authoritative semantic schema compiled by policy expressions.
    ResourceSchema resourceSchema();

    /// Resolves an executable field by its semantic identity.
    Optional<ObjectAuthorizationField> field(FieldId id);

    /// Returns every field accepted by this execution target.
    Collection<ObjectAuthorizationField> fields();

    /// Returns the semantic resource identity accepted by this binding.
    default ResourceType resourceType() {
        return this.resourceSchema().type();
    }

    /// Returns the fingerprint this binding was built against.
    default SchemaFingerprint schemaFingerprint() {
        return this.resourceSchema().fingerprint();
    }

    /// Returns the stable predicate identity of this resource binding.
    default String identity() {
        return this.resourceType().value() + ":" + this.schemaFingerprint().value();
    }
}
