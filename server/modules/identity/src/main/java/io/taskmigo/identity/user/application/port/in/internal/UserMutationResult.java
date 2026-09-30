package io.taskmigo.identity.user.application.port.in.internal;

import io.taskmigo.audit.model.AuditChange;
import java.util.List;
import java.util.UUID;

/// Describes whether one canonical User mutation created or changed persisted User state.
///
/// For an existing User, `changes` contains the complete field-level diff produced by the canonical aggregate mutation.
/// Creation remains outside the current audit scope and therefore carries an empty change list.
public record UserMutationResult(UUID id, boolean created, boolean changed, List<AuditChange> changes) {
    public UserMutationResult {
        changes = List.copyOf(changes);
    }

    public UserMutationResult(UUID id, boolean created, boolean changed) {
        this(id, created, changed, List.of());
    }
}
