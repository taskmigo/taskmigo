package io.taskmigo.identity.group.application;

import java.util.UUID;

/// Describes whether canonical managed Group state was created or changed.
public record GroupMutationResult(UUID id, boolean created, boolean changed) {}
