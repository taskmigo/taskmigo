package io.taskmigo.audit.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Describes one changed field while preventing sensitive values from entering the durable audit path.
public record AuditFieldChange(String field, @Nullable Object before, @Nullable Object after, boolean sensitive) {
    public AuditFieldChange {
        field = Objects.requireNonNull(field);
        if (field.isBlank()) {
            throw new IllegalArgumentException("Audit field must not be blank");
        }
        if (sensitive && (before != null || after != null)) {
            throw new IllegalArgumentException("Sensitive audit changes must not contain values");
        }
    }

    /// Creates a visible before/after field change.
    public static AuditFieldChange visible(String field, @Nullable Object before, @Nullable Object after) {
        return new AuditFieldChange(field, before, after, false);
    }

    /// Records that a sensitive field changed without retaining either value.
    public static AuditFieldChange sensitive(String field) {
        return new AuditFieldChange(field, null, null, true);
    }
}
