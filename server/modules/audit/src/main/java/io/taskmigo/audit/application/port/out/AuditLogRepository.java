package io.taskmigo.audit.application.port.out;

import io.taskmigo.audit.AuditEvent;
import io.taskmigo.audit.AuditLog;
import io.taskmigo.foundation.OffsetPage;

/// Persists and queries immutable audit records.
public interface AuditLogRepository {
    void appendIfAbsent(AuditEvent event);

    OffsetPage<AuditLog> list(String entityType, int page, int pageSize);
}
