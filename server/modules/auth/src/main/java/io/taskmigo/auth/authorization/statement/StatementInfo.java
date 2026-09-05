package io.taskmigo.auth.authorization.statement;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/// Exposes a persisted authorization Statement to authorization evaluators and application consumers.
public record StatementInfo(
    UUID id,
    String name,
    @Nullable String description,
    Effect effect,
    Scope scope,
    TargetInfo target,
    String policy
) {}
