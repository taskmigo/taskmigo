package io.taskmigo.audit.application.service;

import io.taskmigo.audit.application.port.in.api.AuditLogService;
import io.taskmigo.audit.application.port.out.AuditLogQueryRepository;
import io.taskmigo.audit.model.AuditEntityType;
import io.taskmigo.audit.model.AuditLog;
import io.taskmigo.foundation.OffsetPage;

/// Resolves supported entity types before delegating audit-history queries.
public final class DefaultAuditLogService implements AuditLogService {

    private final AuditLogQueryRepository logs;

    public DefaultAuditLogService(AuditLogQueryRepository logs) {
        this.logs = logs;
    }

    @Override
    public OffsetPage<AuditLog> list(String entityType, int page, int perPage) {
        return this.logs.list(AuditEntityType.require(entityType), page, perPage);
    }
}
