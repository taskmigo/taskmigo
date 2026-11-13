package io.taskmigo.audit.model;

/// Records the kind of mutation without guessing from field-level differences.
public enum AuditOperation {
    CREATE,
    UPDATE,
    DELETE,
}
