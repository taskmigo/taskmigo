package io.taskmigo.authorization.request;

/// Opaque handle for the immutable authorization state of one operation.
public interface AuthorizationContext {
    /// Identifies the request attribute used to transport the opaque context between web-layer boundaries.
    String ATTRIBUTE = "taskmigo.authorization.context";
}
