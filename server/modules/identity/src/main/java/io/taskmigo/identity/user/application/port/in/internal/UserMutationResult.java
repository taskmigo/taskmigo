package io.taskmigo.identity.user.application.port.in.internal;

import java.util.UUID;

/// Describes whether one canonical User mutation created or changed persisted User state.
public record UserMutationResult(UUID id, boolean created, boolean changed) {}
