package io.taskmigo.audit.application.port.out;

import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.foundation.OffsetPage;

/// Stores immutable audit records behind the audit application boundary.
public interface AuditLogStore {

    void append(AuditEvent event);

    OffsetPage<AuditLog> list(String entityType, int page, int pageSize);
}
