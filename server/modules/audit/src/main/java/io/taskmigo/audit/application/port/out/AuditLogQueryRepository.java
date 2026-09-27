package io.taskmigo.audit.application.port.out;

import io.taskmigo.audit.model.AuditEntityType;
import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.foundation.OffsetPage;

/// Reads offset-paginated audit history.
public interface AuditLogQueryRepository {
    OffsetPage<AuditLog> list(AuditEntityType entityType, int page, int perPage);
}
