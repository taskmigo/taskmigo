package io.taskmigo.authorization.request.application.model;

import io.taskmigo.authorization.request.AuthorizationContext;

/// Carries the immutable authorization state and normalized target for one authorization operation.
public record AuthorizationOperation(
    AuthorizationSnapshot snapshot,
    String method,
    String path
) implements AuthorizationContext {}
