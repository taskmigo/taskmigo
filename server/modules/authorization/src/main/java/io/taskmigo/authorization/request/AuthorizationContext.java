package io.taskmigo.authorization.request;

import io.taskmigo.language.SchemaContext;

/// Opaque handle for the immutable authorization state of one operation.
public interface AuthorizationContext {
    /// Identifies the request attribute used to transport the opaque context between web-layer boundaries.
    String ATTRIBUTE = "taskmigo.authorization.context";

    /// Returns the provider-neutral context used to select effective runtime resource schemas.
    default SchemaContext schemaContext() {
        return SchemaContext.EMPTY;
    }
}
