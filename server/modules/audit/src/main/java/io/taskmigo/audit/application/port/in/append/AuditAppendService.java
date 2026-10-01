package io.taskmigo.audit.application.port.in.append;

import io.taskmigo.audit.event.AuditEvent;

/// Appends an audit record synchronously inside the caller-owned business transaction.
public interface AuditAppendService {
    /// Persists the final immutable audit record before the owning mutation transaction can commit.
    ///
    /// @param event mutation audit data to append
    void append(AuditEvent event);
}
