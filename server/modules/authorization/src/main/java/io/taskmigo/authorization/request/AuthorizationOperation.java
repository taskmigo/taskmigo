package io.taskmigo.authorization.request;

/// Carries the immutable authorization state and normalized target for one authorization operation.
record AuthorizationOperation(
    AuthorizationSnapshot snapshot,
    String method,
    String path
) implements AuthorizationContext {}
