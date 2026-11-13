package io.taskmigo.audit.application.record;

import io.taskmigo.audit.model.AuditChange;
import io.taskmigo.audit.model.AuditOperation;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/// Records a changed business entity using the responsible actor established at the trusted entry point.
///
/// When audit is enabled, a missing actor fails the owning transaction instead of silently
/// omitting an event. Changes are supplied lazily to avoid unnecessary diff work when disabled.
public interface AuditRecorder {
    /// Records one persisted mutation; callers must not invoke this for an unchanged operation.
    /// Supports entity IDs of any validated textual format, including UUID and OAuth client IDs.
    void record(String entityType, String entityId, AuditOperation operation, Supplier<List<AuditChange>> changes);

    /// Convenience overload for UUID-backed domain aggregates.
    default void record(
        String entityType,
        UUID entityId,
        AuditOperation operation,
        Supplier<List<AuditChange>> changes
    ) {
        this.record(entityType, entityId.toString(), operation, changes);
    }
}
