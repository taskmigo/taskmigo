package io.taskmigo.authorization.request.application.api;

import io.taskmigo.authorization.request.AuthorizationPrincipal;
import io.taskmigo.authorization.request.AuthorizationRequest;
import io.taskmigo.authorization.request.RequestAuthorizationResult;

/// Authorizes a typed principal/request pair without exposing policy-evaluation mechanics to callers.
public interface RequestAuthorization {
    /// Evaluates the request and returns its decision together with the reusable same-operation context.
    RequestAuthorizationResult authorize(AuthorizationPrincipal principal, AuthorizationRequest request);
}
