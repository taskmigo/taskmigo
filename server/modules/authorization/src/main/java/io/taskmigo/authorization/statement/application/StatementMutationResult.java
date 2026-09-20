package io.taskmigo.authorization.statement.application;

import java.util.UUID;

/// Describes the canonical mutation outcome used by managed Statement reconciliation.
public record StatementMutationResult(UUID id, boolean created, boolean changed) {}
