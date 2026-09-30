package io.taskmigo.audit.application.port.in.query;

import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.foundation.OffsetPage;

/// Queries immutable audit records.
public interface AuditQueryService {
    /// Returns one newest-first page for a supported entity type.
    ///
    /// @param entityType entity type route value
    /// @param page one-based page number
    /// @param pageSize requested page size
    /// @return matching audit records
    OffsetPage<AuditLog> list(String entityType, int page, int pageSize);
}
