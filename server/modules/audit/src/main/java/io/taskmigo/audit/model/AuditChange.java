package io.taskmigo.audit.model;

import org.jspecify.annotations.Nullable;

/// Describes one field-level change without ever carrying values for sensitive fields.
///
/// @param field stable field name
/// @param before previous value for a non-sensitive field
/// @param after new value for a non-sensitive field
/// @param sensitive whether values are intentionally omitted
public record AuditChange(String field, @Nullable Object before, @Nullable Object after, boolean sensitive) {
    public AuditChange {
        if (sensitive && (before != null || after != null)) {
            throw new IllegalArgumentException("Sensitive audit changes cannot contain values");
        }
    }

    /// Creates a visible field diff.
    public static AuditChange visible(String field, @Nullable Object before, @Nullable Object after) {
        return new AuditChange(field, before, after, false);
    }

    /// Creates a sensitive-field marker whose values are omitted before audit persistence.
    public static AuditChange sensitive(String field) {
        return new AuditChange(field, null, null, true);
    }
}
