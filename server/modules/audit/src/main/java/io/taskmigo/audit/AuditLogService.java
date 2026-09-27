package io.taskmigo.audit;

import io.taskmigo.foundation.OffsetPage;

/// Queries persisted audit records.
public interface AuditLogService {
    OffsetPage<AuditLog> list(String entityType, int page, int pageSize);
}
