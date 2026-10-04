package io.taskmigo.authorization.request.application.model;

import io.taskmigo.authorization.request.AuthorizationContext;
import io.taskmigo.language.SchemaContext;
import java.util.Objects;

/// Carries the immutable authorization state, normalized target, and effective schema context for one operation.
public record AuthorizationOperation(
    AuthorizationSnapshot snapshot,
    String method,
    String path,
    SchemaContext schemaContext
) implements AuthorizationContext {

    /// Creates an operation without additional runtime schema inputs.
    public AuthorizationOperation(AuthorizationSnapshot snapshot, String method, String path) {
        this(snapshot, method, path, SchemaContext.EMPTY);
    }

    public AuthorizationOperation {
        Objects.requireNonNull(snapshot);
        Objects.requireNonNull(method);
        Objects.requireNonNull(path);
        Objects.requireNonNull(schemaContext);
    }
}
