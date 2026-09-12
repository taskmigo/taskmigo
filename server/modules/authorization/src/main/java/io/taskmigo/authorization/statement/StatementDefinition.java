package io.taskmigo.authorization.statement;

import org.jspecify.annotations.Nullable;

/// Represents a validated authorization Statement definition before persistence assigns its identifier.
public record StatementDefinition(
    String name,
    @Nullable String description,
    Effect effect,
    Scope scope,
    String method,
    String path,
    String policy
) {}
