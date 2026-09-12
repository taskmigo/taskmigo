package io.taskmigo.authorization.request;

/// Authorizes a typed principal/request pair without exposing language root maps to callers.
public interface RequestAuthorization {
    /// Evaluates the request and returns its decision together with the operation context.
    RequestAuthorizationResult authorize(AuthorizationPrincipal principal, AuthorizationRequest request);
}
