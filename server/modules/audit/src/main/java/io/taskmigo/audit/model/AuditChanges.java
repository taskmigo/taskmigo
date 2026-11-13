package io.taskmigo.audit.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Builds an explicit, ordered field diff without reflection or accidental secret serialization.
public final class AuditChanges {

    private final List<AuditChange> changes = new ArrayList<>();

    private AuditChanges() {}

    public static AuditChanges builder() {
        return new AuditChanges();
    }

    /// Includes an already classified field change.
    public AuditChanges add(AuditChange change) {
        this.changes.add(Objects.requireNonNull(change));
        return this;
    }

    /// Includes a non-sensitive field only if its value changed.
    public AuditChanges changed(String field, @Nullable Object before, @Nullable Object after) {
        if (!Objects.equals(before, after)) {
            this.changes.add(AuditChange.visible(field, before, after));
        }
        return this;
    }

    /// Records a sensitive field name without retaining either value when its value changed.
    public AuditChanges sensitiveChanged(String field, @Nullable Object before, @Nullable Object after) {
        if (!Objects.equals(before, after)) {
            this.changes.add(AuditChange.sensitive(field));
        }
        return this;
    }

    public List<AuditChange> build() {
        return List.copyOf(this.changes);
    }
}
