package io.taskmigo.authorization.request;

/// Contains the Request Authorization decision and its reusable same-operation context.
public record RequestAuthorizationResult(boolean granted, AuthorizationContext context) {}
