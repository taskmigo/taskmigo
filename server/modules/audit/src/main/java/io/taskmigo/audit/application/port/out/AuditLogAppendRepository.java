package io.taskmigo.audit.application.port.out;

import io.taskmigo.audit.model.AuditLog;
import java.util.UUID;

/// Persists immutable audit entries with source-event idempotency.
public interface AuditLogAppendRepository {
    boolean existsBySourceEventId(UUID sourceEventId);
    void append(AuditLog log);
}
