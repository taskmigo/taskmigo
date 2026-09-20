package io.taskmigo.authorization.role.application;

import java.util.UUID;

/// Describes whether canonical managed Role state was created or changed.
public record RoleMutationResult(UUID id, boolean created, boolean changed) {}
