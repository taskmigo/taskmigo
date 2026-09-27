package io.taskmigo.audit.application.port.in.api;

import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.foundation.OffsetPage;

/// Queries immutable audit history.
public interface AuditLogService {
    OffsetPage<AuditLog> list(String entityType, int page, int perPage);
}
