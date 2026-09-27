package io.taskmigo.audit;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Describes one changed field while preventing sensitive values from entering the audit pipeline.
public record AuditChange(String field, boolean sensitive, @Nullable Object beforeValue, @Nullable Object afterValue) {
    public AuditChange {
        Objects.requireNonNull(field);
        if (sensitive && (beforeValue != null || afterValue != null)) {
            throw new IllegalArgumentException("Sensitive audit changes cannot contain values");
        }
    }

    public static AuditChange visible(String field, @Nullable Object beforeValue, @Nullable Object afterValue) {
        return new AuditChange(field, false, beforeValue, afterValue);
    }

    public static AuditChange sensitive(String field) {
        return new AuditChange(field, true, null, null);
    }
}
