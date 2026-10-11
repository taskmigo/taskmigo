package io.taskmigo.identity.user.application;

import io.taskmigo.audit.event.AuditEvent;

/// Appends User mutation audit records synchronously inside the owning transaction.
public interface UserAuditAppender {
    void append(AuditEvent event);
}
