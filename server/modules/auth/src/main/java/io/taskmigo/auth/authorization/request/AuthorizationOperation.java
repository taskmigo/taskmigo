package io.taskmigo.auth.authorization.request;

/// Carries the immutable authorization state and normalized target for one authorization operation.
public record AuthorizationOperation(AuthorizationSnapshot snapshot, String method, String path) {
    /// Identifies the request attribute used to transport the operation between web-layer boundaries.
    public static final String ATTRIBUTE = "taskmigo.authorization.operation";
}
