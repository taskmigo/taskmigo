package io.taskmigo.authorization.request;

import java.util.UUID;

/// Identifies the authenticated principal supplied to Request Authorization.
public record AuthorizationPrincipal(UUID id, String username) {}
