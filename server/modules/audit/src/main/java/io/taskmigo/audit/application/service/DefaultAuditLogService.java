package io.taskmigo.audit.application.service;

import io.taskmigo.audit.AuditException;
import io.taskmigo.audit.AuditLog;
import io.taskmigo.audit.AuditLogService;
import io.taskmigo.audit.application.port.out.AuditLogRepository;
import io.taskmigo.audit.application.port.out.AuditTransactionRunner;
import io.taskmigo.foundation.OffsetPage;

/// Default audit query application service.
public final class DefaultAuditLogService implements AuditLogService {

    private static final String USER_ENTITY_TYPE = "user";

    private final AuditLogRepository logs;
    private final AuditTransactionRunner transactions;

    public DefaultAuditLogService(AuditLogRepository logs, AuditTransactionRunner transactions) {
        this.logs = logs;
        this.transactions = transactions;
    }

    @Override
    public OffsetPage<AuditLog> list(String entityType, int page, int pageSize) {
        if (!USER_ENTITY_TYPE.equals(entityType)) {
            throw new AuditException("Unsupported audit entity type: " + entityType);
        }
        return this.transactions.read(() -> this.logs.list(entityType, page, pageSize));
    }
}
