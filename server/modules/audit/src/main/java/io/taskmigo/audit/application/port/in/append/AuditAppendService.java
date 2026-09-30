package io.taskmigo.audit.application.port.in.append;

import io.taskmigo.audit.event.AuditEvent;

/// Appends an externalized audit event idempotently.
public interface AuditAppendService {
    /// Persists the final audit record, treating an already-appended source event as success.
    ///
    /// @param event durable mutation event to append
    void append(AuditEvent event);
}
