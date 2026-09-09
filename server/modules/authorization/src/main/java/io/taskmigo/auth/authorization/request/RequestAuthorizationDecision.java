package io.taskmigo.auth.authorization.request;

/// Represents the result of evaluating a request authorization policy.
public record RequestAuthorizationDecision(boolean allowed) {}
