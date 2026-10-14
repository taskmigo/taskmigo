package io.taskmigo.audit.application;

import io.taskmigo.audit.event.AuditEvent;
import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.foundation.OffsetPage;
import java.util.UUID;

/// Stores immutable audit records behind the Audit application boundary.
public interface AuditLogStore {
    void append(AuditEvent event);

    OffsetPage<AuditLog> list(String entityType, int page, int pageSize);

    void scrubUser(UUID userId);
}
